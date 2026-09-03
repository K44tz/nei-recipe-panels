package com.neirecipepanels;

import codechicken.nei.api.IConfigureNEI;

public class NEIRecipePanelsConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        NeiRecipePanels.LOG.info("NEI plugin loaded");
    }

    @Override
    public String getName() {
        return "NEI Recipe Panels";
    }

    @Override
    public String getVersion() {
        return Tags.VERSION;
    }
}
