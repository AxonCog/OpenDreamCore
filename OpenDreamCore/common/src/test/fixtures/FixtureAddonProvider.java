package com.opendreamcore.fixtures;

import com.opendreamcore.core.addons.AddonProvider;

/** addons fixture：打进 fixture-provider.jar（含 META-INF/services 注册文件）供加载测试用。 */
public class FixtureAddonProvider implements AddonProvider {

    private final String tag;

    public FixtureAddonProvider() {
        this("v1");
    }

    public FixtureAddonProvider(String tag) {
        this.tag = tag;
    }

    @Override
    public String id() {
        return "fixture-addon";
    }

    @Override
    public String type() {
        return "test";
    }

    @Override
    public String version() {
        return tag;
    }
}
