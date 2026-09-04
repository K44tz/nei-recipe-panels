package com.neirecipepanels.client;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fluids.IFluidContainerItem;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.google.gson.JsonParser;
import com.neirecipepanels.NeiRecipePanels;
import com.neirecipepanels.PanelSettings;
import com.neirecipepanels.RecipeSnapshot;
import com.neirecipepanels.block.RecipePanelTile;

import codechicken.nei.PositionedStack;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.Badge;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.RecipeHandlerRef;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.interfaces.IGT_ItemWithMaterialRenderer;

/**
 * Renders each placed recipe into an offscreen framebuffer with NEI's own handler + item
 * drawing, so the TESR can blit it as a single flat quad.
 */
public final class PanelFboManager {

    public static final PanelFboManager INSTANCE = new PanelFboManager();

    /** Padding around the recipe inside the square framebuffer, in recipe pixels. */
    private static final int INNER_PAD = 14;
    private static final int MIN_SIDE = 176;
    private static final int MAX_SIDE = 360;
    /** Render the framebuffer at this multiple of recipe pixels so text stays legible on the wall. */
    private static final int SUPERSAMPLE = 3;
    /** Re-render an on-screen panel this often so animated GT textures keep ticking. */
    private static final int ANIM_INTERVAL_FRAMES = 3;
    private static final int EVICT_AFTER_FRAMES = 200;

    private final Map<RecipePanelTile, Panel> panels = new IdentityHashMap<>();
    private final Map<RecipePanelTile, Integer> lastSeen = new HashMap<>();
    private int frame;

    private PanelFboManager() {}

    /** Drop every cached render so panels rebuild against the reloaded resources (theme colours, atlas). */
    public void reload() {
        for (Panel panel : panels.values()) {
            panel.dispose();
        }
        panels.clear();
        lastSeen.clear();
    }

    /** Called by the TESR for every panel it draws this frame. */
    public Panel visible(RecipePanelTile tile) {
        lastSeen.put(tile, frame);
        Panel panel = panels.get(tile);
        if (panel == null) {
            panel = new Panel();
            panels.put(tile, panel);
        }
        return panel;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || panels.isEmpty()) return;
        if (!OpenGlHelper.framebufferSupported) return;
        frame++;

        boolean rendered = false;
        for (Map.Entry<RecipePanelTile, Panel> entry : panels.entrySet()) {
            RecipePanelTile tile = entry.getKey();
            Panel panel = entry.getValue();
            NBTTagCompound snapshot = tile.getSnapshot();
            if (snapshot == null || panel.resolveFailed) continue;
            NBTTagCompound settings = tile.getSettings();

            Integer seen = lastSeen.get(tile);
            boolean onScreen = seen != null && frame - seen <= 2;
            boolean due = panel.isStale(snapshot, settings)
                || (onScreen && frame - panel.lastRenderFrame >= ANIM_INTERVAL_FRAMES);
            if (due) {
                panel.render(snapshot, settings);
                panel.lastRenderFrame = frame;
                rendered = true;
            }
        }
        if (rendered) {
            Minecraft.getMinecraft()
                .getFramebuffer()
                .bindFramebuffer(true);
        }

        Iterator<Map.Entry<RecipePanelTile, Panel>> it = panels.entrySet()
            .iterator();
        while (it.hasNext()) {
            Map.Entry<RecipePanelTile, Panel> entry = it.next();
            Integer seen = lastSeen.get(entry.getKey());
            if (seen == null || frame - seen > EVICT_AFTER_FRAMES) {
                entry.getValue()
                    .dispose();
                lastSeen.remove(entry.getKey());
                it.remove();
            }
        }
    }

    public static final class Panel {

        Framebuffer fbo;
        int width;
        int height;
        boolean resolveFailed;
        private NBTTagCompound renderedFor;
        private NBTTagCompound renderedSettings;
        private boolean transparent;
        private IRecipeHandler handler;
        private int recipeIndex;
        private int yShift;
        private int originX;
        private int originY;
        private RecipeSnapshot frozen;
        int lastRenderFrame = Integer.MIN_VALUE;

        boolean isStale(NBTTagCompound snapshot, NBTTagCompound settings) {
            return renderedFor != snapshot || renderedSettings != settings;
        }

        boolean ready() {
            return fbo != null && !resolveFailed;
        }

        boolean transparent() {
            return transparent;
        }

        void bindTexture() {
            fbo.bindFramebufferTexture();
        }

        void dispose() {
            if (fbo != null) {
                fbo.deleteFramebuffer();
                fbo = null;
            }
        }

        /** The frozen recipe's item stack at a given FBO pixel, or null off any slot. For Waila lookups. */
        ItemStack stackAt(int fx, int fy) {
            if (frozen == null) return null;
            for (RecipeSnapshot.Slot slot : frozen.ingredients) {
                if (inSlot(fx, fy, slot.relx, slot.rely)) return slot.stack;
            }
            for (RecipeSnapshot.Slot slot : frozen.others) {
                if (inSlot(fx, fy, slot.relx, slot.rely)) return slot.stack;
            }
            if (frozen.result != null && inSlot(fx, fy, frozen.resultX, frozen.resultY)) return frozen.result;
            return null;
        }

        private boolean inSlot(int fx, int fy, int relx, int rely) {
            int sx = originX + relx;
            int sy = originY + yShift + rely;
            return fx >= sx && fx < sx + 16 && fy >= sy && fy < sy + 16;
        }

        private void resolve(NBTTagCompound snapshot) {
            try {
                String json = RecipeSnapshot.peekRecipeId(snapshot);
                if (json == null || json.isEmpty()) {
                    resolveFailed = true;
                    return;
                }
                Recipe.RecipeId recipeId = Recipe.RecipeId.of(
                    new JsonParser().parse(json)
                        .getAsJsonObject());
                RecipeHandlerRef ref = RecipeHandlerRef.of(recipeId);
                if (ref == null || ref.handler == null) {
                    resolveFailed = true;
                    return;
                }
                handler = ref.handler;
                recipeIndex = ref.recipeIndex;
                // NEI's GuiRecipe calls this every frame; GT's handler lazily reads its themed NEI
                // text colour here, so prime it before drawForeground draws the recipe description.
                handler.getRecipeName();
                frozen = RecipeSnapshot.readFromNBT(snapshot);
                HandlerInfo info = GuiRecipeTab.getHandlerInfo(handler);
                yShift = info != null ? info.getYShift() : 0;

                // bounding box over the handler's declared area AND its own stacks - GT machine
                // recipes draw slots / backgrounds past the HandlerInfo box.
                int[] box = { 0, 0, info != null ? info.getWidth() : HandlerInfo.DEFAULT_WIDTH,
                    info != null ? info.getHeight() : HandlerInfo.DEFAULT_HEIGHT };
                for (RecipeSnapshot.Slot s : frozen.ingredients) growBox(box, s.relx, s.rely);
                for (RecipeSnapshot.Slot s : frozen.others) growBox(box, s.relx, s.rely);
                if (frozen.result != null) growBox(box, frozen.resultX, frozen.resultY);
                int x0 = box[0];
                int y0 = box[1];
                int bw = box[2] - box[0];
                int bh = box[3] - box[1];

                // square FBO, recipe rendered 1:1 (no glScalef - GT's custom item renderer
                // mishandles a non-unit modelview scale). The blit keeps every panel the same size.
                int side = MathHelper.clamp_int(Math.max(bw, bh) + 2 * INNER_PAD, MIN_SIDE, MAX_SIDE);
                width = side;
                height = side;
                originX = (side - bw) / 2 - x0;
                originY = (side - bh) / 2 - y0;
            } catch (Throwable t) {
                NeiRecipePanels.LOG.warn("Recipe panel: could not resolve recipe", t);
                resolveFailed = true;
            }
        }

        private void render(NBTTagCompound snapshot, NBTTagCompound settingsNbt) {
            if (handler == null) {
                resolve(snapshot);
                if (resolveFailed) return;
            }
            PanelSettings settings = PanelSettings.fromNBT(settingsNbt);
            transparent = settings.transparent;

            int fw = width;
            int fh = height;
            int texW = fw * SUPERSAMPLE;
            int texH = fh * SUPERSAMPLE;
            if (fbo == null || fbo.framebufferWidth != texW || fbo.framebufferHeight != texH) {
                if (fbo != null) fbo.deleteFramebuffer();
                // depth buffer: GuiContainerManager.drawItem enables GL_DEPTH_TEST, and GT's
                // multi-pass icons / 3D block models need it to composite and self-occlude.
                fbo = new Framebuffer(texW, texH, true);
                fbo.setFramebufferFilter(GL11.GL_LINEAR);
            }

            fbo.framebufferColor[0] = 0F;
            fbo.framebufferColor[1] = 0F;
            fbo.framebufferColor[2] = 0F;
            fbo.framebufferColor[3] = 0F;
            fbo.bindFramebuffer(true);
            GL11.glClearColor(0F, 0F, 0F, 0F);
            GL11.glClearDepth(1D);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glDepthFunc(GL11.GL_LEQUAL);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            // ortho stays in recipe-pixel units; the larger viewport does the supersampling
            GL11.glOrtho(0D, fw, fh, 0D, 1000D, 3000D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glTranslatef(0F, 0F, -2000F);

            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glColor4f(1F, 1F, 1F, 1F);
            if (!settings.transparent) {
                drawGuiBackdrop(fw, fh);
            }
            drawNamePlate(fw, settings);

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glTranslatef(originX, originY + yShift, 0F);
            try {
                handler.drawBackground(recipeIndex);
                GL11.glColor4f(1F, 1F, 1F, 1F);
                GL11.glEnable(GL11.GL_ALPHA_TEST);
                GL11.glDisable(GL11.GL_BLEND);
                RenderHelper.enableGUIStandardItemLighting();
                // frozen permutations: draw the stacks captured at imprint time, not NEI's live
                // (cycling) ones. Animated icons still tick because that is atlas-level.
                for (RecipeSnapshot.Slot slot : frozen.ingredients) {
                    drawStack(slot.relx, slot.rely, slot.stack);
                }
                for (RecipeSnapshot.Slot slot : frozen.others) {
                    drawStack(slot.relx, slot.rely, slot.stack);
                }
                if (frozen.result != null) {
                    drawStack(frozen.resultX, frozen.resultY, frozen.result);
                }
                RenderHelper.disableStandardItemLighting();
                GL11.glColor4f(1F, 1F, 1F, 1F);
                for (RecipeSnapshot.Slot slot : frozen.ingredients) {
                    drawBadge(slot, true);
                }
                for (RecipeSnapshot.Slot slot : frozen.others) {
                    drawBadge(slot, false);
                }
                handler.drawForeground(recipeIndex);
            } catch (Throwable t) {
                NeiRecipePanels.LOG.warn("Recipe panel: handler draw failed", t);
                resolveFailed = true;
            }

            // GT's item renderers can zero the alpha channel, which would then show the world
            // through the panel on the blit. Restore it: re-stamp the backdrop's own (possibly
            // rounded) shape for a solid panel, or just the slot rects when it stays see-through.
            GL11.glColorMask(false, false, false, true);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_LIGHTING);
            if (settings.transparent) {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glColor4f(0F, 0F, 0F, 1F);
                Tessellator opaque = Tessellator.instance;
                opaque.startDrawingQuads();
                for (RecipeSnapshot.Slot slot : frozen.ingredients) slotAlphaQuad(opaque, slot.relx, slot.rely);
                for (RecipeSnapshot.Slot slot : frozen.others) slotAlphaQuad(opaque, slot.relx, slot.rely);
                if (frozen.result != null) slotAlphaQuad(opaque, frozen.resultX, frozen.resultY);
                opaque.draw();
            } else {
                GL11.glLoadIdentity();
                GL11.glTranslatef(0F, 0F, -2000F);
                drawGuiBackdrop(fw, fh);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
            }
            GL11.glColorMask(true, true, true, true);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            fbo.unbindFramebuffer();
            buildMipmaps();
            renderedFor = snapshot;
            renderedSettings = settingsNbt;
        }

        /** Recipe name (or the panel's custom name) as a title along the top edge. */
        private void drawNamePlate(int fw, PanelSettings settings) {
            String text = settings.customName.isEmpty() ? frozen.recipeName : settings.customName;
            if (text == null || text.isEmpty()) return;
            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            text = font.trimStringToWidth(text, fw - 2 * BORDER - 8);
            int x = (fw - font.getStringWidth(text)) / 2;
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            if (settings.transparent) {
                font.drawStringWithShadow(text, x, 5, PanelColors.text("text_white", 0xFFFFFF));
            } else {
                font.drawString(text, x, 5, PanelColors.text("title", 0x404040));
            }
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }

        /** Mip the supersampled texture so text doesn't crawl when the panel is far / at an angle. */
        private void buildMipmaps() {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, fbo.framebufferTexture);
            boolean mipped = false;
            try {
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                mipped = true;
            } catch (Throwable ignored) {
                // driver without glGenerateMipmap; fall back to plain linear
            }
            GL11.glTexParameteri(
                GL11.GL_TEXTURE_2D,
                GL11.GL_TEXTURE_MIN_FILTER,
                mipped ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }

        private static final ResourceLocation GUI_BG = new ResourceLocation("textures/gui/demo_background.png");
        private static final int BG_TEX = 256;
        private static final int BG_SRC_W = 248;
        private static final int BG_SRC_H = 166;
        private static final int BORDER = 4;

        /** Nine-slice the vanilla demo GUI texture to fill the framebuffer at any size. */
        private static void drawGuiBackdrop(int w, int h) {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(GUI_BG);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glColor4f(1F, 1F, 1F, 1F);

            int b = BORDER;
            int dcx = Math.max(0, w - 2 * b);
            int dcy = Math.max(0, h - 2 * b);
            int scx = BG_SRC_W - 2 * b;
            int scy = BG_SRC_H - 2 * b;

            blit(0, 0, b, b, 0, 0, b, b);
            blit(w - b, 0, b, b, BG_SRC_W - b, 0, b, b);
            blit(0, h - b, b, b, 0, BG_SRC_H - b, b, b);
            blit(w - b, h - b, b, b, BG_SRC_W - b, BG_SRC_H - b, b, b);
            blit(b, 0, dcx, b, b, 0, scx, b);
            blit(b, h - b, dcx, b, b, BG_SRC_H - b, scx, b);
            blit(0, b, b, dcy, 0, b, b, scy);
            blit(w - b, b, b, dcy, BG_SRC_W - b, b, b, scy);
            blit(b, b, dcx, dcy, b, b, scx, scy);
        }

        private static void blit(int dx, int dy, int dw, int dh, int sx, int sy, int sw, int sh) {
            float u0 = sx / (float) BG_TEX;
            float v0 = sy / (float) BG_TEX;
            float u1 = (sx + sw) / (float) BG_TEX;
            float v1 = (sy + sh) / (float) BG_TEX;
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            t.addVertexWithUV(dx, dy + dh, 0D, u0, v1);
            t.addVertexWithUV(dx + dw, dy + dh, 0D, u1, v1);
            t.addVertexWithUV(dx + dw, dy, 0D, u1, v0);
            t.addVertexWithUV(dx, dy, 0D, u0, v0);
            t.draw();
        }

        /**
         * A padded quad over one slot for the transparent-panel alpha stamp. The pad covers the
         * slot border and closes the gaps between adjacent slots so a grid reads as one solid block.
         */
        private static void slotAlphaQuad(Tessellator t, int x, int y) {
            t.addVertex(x - 2, y + 18, 0D);
            t.addVertex(x + 18, y + 18, 0D);
            t.addVertex(x + 18, y - 2, 0D);
            t.addVertex(x - 2, y - 2, 0D);
        }

        private static void growBox(int[] box, int px, int py) {
            box[0] = Math.min(box[0], px);
            box[1] = Math.min(box[1], py);
            box[2] = Math.max(box[2], px + 16);
            box[3] = Math.max(box[3], py + 16);
        }

        /**
         * NEI's per-slot corner badge ("NC", chance %). Drawn here rather than through
         * {@link Badge#draw} because that path needs an open GuiScreen for its scale factor.
         */
        private static void drawBadge(RecipeSnapshot.Slot slot, boolean input) {
            Badge badge = badgeFor(slot, input);
            if (badge == null) {
                return;
            }
            String text = badge.getText();
            if (text == null || text.isEmpty()) {
                return;
            }
            GL11.glPushMatrix();
            GL11.glScalef(0.5F, 0.5F, 1F);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            Minecraft.getMinecraft().fontRenderer.drawString(text, slot.relx * 2, slot.rely * 2 + 1, badge.getColor());
            GL11.glPopMatrix();
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }

        private static Badge badgeFor(RecipeSnapshot.Slot slot, boolean input) {
            if (slot.stack == null) {
                return null;
            }
            if (input) {
                if (slot.notConsumed) {
                    return Badge.notConsumed();
                }
                if (slot.chance == 0) {
                    return Badge.notConsumedParallel();
                }
                if (slot.chance != PositionedStack.CHANCE_FULL) {
                    return Badge.consumeChance(slot.chance / 10000F);
                }
            } else if (slot.chance != PositionedStack.CHANCE_FULL && slot.chance != 0) {
                return Badge.outputChance(slot.chance / 10000F);
            }
            return null;
        }

        /** Isolate each item so a mod's custom item renderer can't leak GL matrix / colour state. */
        static void drawStack(int x, int y, ItemStack stack) {
            if (stack == null) return;
            GL11.glPushMatrix();
            GL11.glColor4f(1F, 1F, 1F, 1F);
            if (useFlatIcon(stack)) {
                drawFlatIcon(x, y, stack);
            } else {
                GuiContainerManager.drawItem(x, y, stack);
            }
            GL11.glColor4f(1F, 1F, 1F, 1F);
            GL11.glPopMatrix();
        }

        /**
         * True for GT's meta-generated items (gears, plates, dusts, ...) - their custom renderer
         * garbles outside the standard GUI transform, so we draw a plain flat multi-pass icon
         * instead. Scoped to GT specifically: other mods' custom item renderers (AE2 cables, GT
         * fluid cells) render fine through the normal path and should keep their real look.
         */
        private static boolean useFlatIcon(ItemStack stack) {
            Item item = stack.getItem();
            if (!(item instanceof IGT_ItemWithMaterialRenderer) || item instanceof IFluidContainerItem) {
                return false;
            }
            NBTTagCompound tag = stack.getTagCompound();
            if (tag != null && (tag.hasKey("mFluidDisplayAmount") || tag.hasKey("mFluidDisplayHeat"))) {
                return false; // GT fluid-display stack: only its custom renderer knows the fluid icon
            }
            if (MinecraftForgeClient.getItemRenderer(stack, IItemRenderer.ItemRenderType.INVENTORY) == null) {
                return false;
            }
            return item.getIcon(stack, 0) != null;
        }

        private static void drawFlatIcon(int x, int y, ItemStack stack) {
            Item item = stack.getItem();
            Minecraft mc = Minecraft.getMinecraft();
            RenderItem render = GuiContainerManager.drawItems;
            render.zLevel = 0F;
            int meta = stack.getItemDamage();
            boolean multi = item.requiresMultipleRenderPasses();
            int passes = multi ? item.getRenderPasses(meta) : 1;

            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            for (int pass = 0; pass < passes; pass++) {
                IIcon icon = multi ? item.getIcon(stack, pass) : item.getIconIndex(stack);
                if (icon == null) continue;
                // some items (e.g. AE2 parts) source their icon from the block atlas, not the
                // item one - getSpriteNumber says which, same as vanilla's own multi-pass draw.
                mc.getTextureManager()
                    .bindTexture(
                        item.getSpriteNumber() == 0 ? TextureMap.locationBlocksTexture
                            : TextureMap.locationItemsTexture);
                int c = item.getColorFromItemStack(stack, pass);
                GL11.glColor4f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F, 1F);
                render.renderIcon(x, y, icon, 16, 16);
            }
            GL11.glColor4f(1F, 1F, 1F, 1F);
            GL11.glDisable(GL11.GL_BLEND);
            render.renderItemOverlayIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
        }
    }
}
