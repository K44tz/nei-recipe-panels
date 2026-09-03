package com.neirecipepanels.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

import com.neirecipepanels.NeiRecipePanels;
import com.neirecipepanels.PanelSettings;
import com.neirecipepanels.block.RecipePanelTile;
import com.neirecipepanels.network.ConfigurePanelMessage;

/** Shift-right-click screen for renaming a placed panel and toggling its background. */
public class GuiRecipePanelConfig extends GuiScreen {

    private final RecipePanelTile tile;
    private final String initialName;
    private final boolean initialTransparent;

    private GuiTextField nameField;
    private GuiButton transparentButton;
    private boolean transparent;

    public GuiRecipePanelConfig(RecipePanelTile tile) {
        this.tile = tile;
        PanelSettings settings = tile.settings();
        this.initialName = settings.customName;
        this.initialTransparent = settings.transparent;
        this.transparent = settings.transparent;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        int cx = width / 2;

        nameField = new GuiTextField(fontRendererObj, cx - 100, height / 2 - 30, 200, 20);
        nameField.setMaxStringLength(PanelSettings.MAX_NAME);
        nameField.setText(initialName);
        nameField.setFocused(true);

        transparentButton = new GuiButton(1, cx - 100, height / 2, 200, 20, transparentLabel());
        buttonList.add(transparentButton);
        buttonList
            .add(new GuiButton(0, cx - 100, height / 2 + 28, 200, 20, StatCollector.translateToLocal("gui.done")));
    }

    private String transparentLabel() {
        return "Background: " + (transparent ? "Hidden" : "Shown");
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0:
                mc.thePlayer.closeScreen();
                break;
            case 1:
                transparent = !transparent;
                transparentButton.displayString = transparentLabel();
                break;
            default:
        }
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            mc.thePlayer.closeScreen();
            return;
        }
        if ((key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) && nameField.isFocused()) {
            mc.thePlayer.closeScreen();
            return;
        }
        nameField.textboxKeyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        nameField.mouseClicked(x, y, button);
    }

    @Override
    public void updateScreen() {
        nameField.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        String typed = nameField.getText()
            .trim();
        String name = PanelSettings.trim(typed);
        if (name.equals(initialName) && transparent == initialTransparent) {
            return;
        }
        NeiRecipePanels.NETWORK
            .sendToServer(new ConfigurePanelMessage(tile.xCoord, tile.yCoord, tile.zCoord, name, transparent));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Recipe Panel", width / 2, height / 2 - 55, 0xFFFFFF);
        drawString(fontRendererObj, "Name", width / 2 - 100, height / 2 - 42, 0xA0A0A0);
        nameField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
