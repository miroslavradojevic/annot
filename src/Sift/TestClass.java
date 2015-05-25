package Sift;

import ij.IJ;
import ij.plugin.PlugIn;

/**
 * Created by miroslav on 25-5-15.
 */
public class TestClass implements PlugIn {
    @Override
    public void run(String s) {
        IJ.log("running...");
    }
}
