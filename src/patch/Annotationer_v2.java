package annot.src.patch;

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.*;
import ij.io.FileSaver;
import ij.io.OpenDialog;
import ij.plugin.Duplicator;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Random;

/**
 * Created by miroslav on 9-2-15.
 */
public class Annotationer_v2 implements PlugIn {

    ImagePlus inimg;

    int n, j = 0;


    String image_path;
    ImageCanvas canvas;
    ImageWindow wind;
    String[] namesGroups;
    Overlay ov_rectangle = new Overlay();

    float x1, y1, x2, y2;

  
    @Override
    public void run(String s) {

        //to open the dialog with the settings
        MainPanel_v2 panelMain = new MainPanel_v2();
        GenericDialog gdG = new GenericDialog("Annotationer");
        gdG.add(panelMain);
        gdG.addMessage("");
        MainPanel_v2 panelD = (MainPanel_v2) gdG.getComponent(0);
        gdG.showDialog();
        if (gdG.wasCanceled()) {
            return;
        }

        String path=panelD.getTxtUrl();
        
        if (path.isEmpty()) {
            IJ.error("You have to write an url for the results.");
            return;
        }

        System.out.println("patch annotationer...");

        // select the image
        String in_folder = Prefs.get("id.folder", System.getProperty("user.home"));
        OpenDialog.setDefaultDirectory(in_folder);
        OpenDialog dc = new OpenDialog("Select file");
        in_folder = dc.getDirectory();
        image_path = dc.getPath();
        if (image_path == null) {
            return;
        }
        Prefs.set("id.folder", in_folder);

        inimg = new ImagePlus(image_path);

        if (inimg == null) {
            return;
        }
        if (!getFileExtension(inimg.getTitle()).equalsIgnoreCase("TIF")) {
            IJ.log("open image with .TIF extension");
            return;
        }

//        IJ.log(image_path);
        inimg.show();
        wind = inimg.getWindow();
        canvas = inimg.getCanvas();
        ImageCanvas can = wind.getCanvas();

        wind.removeKeyListener(IJ.getInstance());
        can.removeKeyListener(IJ.getInstance());
        canvas.removeKeyListener(IJ.getInstance());

        //properties of image
        int w = inimg.getWidth();
        int h = inimg.getHeight();
        ImageProcessor ip = inimg.getProcessor();
        ArrayList<Roi> roiList = new ArrayList<Roi>();
        int wfix = panelD.getTxtWidthRand();
        int hfix = panelD.getTxtHeightRand();

        Random r = new Random();
        for (int i = 0; i < panelD.getNumPatches(); i++) {
            //generate a random number between the size of images
            int wR = r.nextInt(w);
            int hR = r.nextInt(h);
            while ((wR + wfix) > w || (hR + hfix) > h) {
                wR = r.nextInt(w);
                hR = r.nextInt(h);
            }
            Roi roi = new Roi(wR, hR, wfix, hfix);
            roiList.add(roi);
            ip = inimg.getProcessor();
            ip.setRoi(roi);
//            inimg.setRoi(roi);
//            IJ.setForegroundColor(255, 255, 255);
//            IJ.run(inimg, "Draw", "");
//            inimg.updateAndDraw();
            ip = ip.crop();
            String aa= "random_";
            ImagePlus impCopy = new ImagePlus(aa, ip);
            
            String auxPath = path + File.separator + aa;
            File url = new File(auxPath);
            url.mkdirs();
            new FileSaver(impCopy).saveAsTiff(auxPath + File.separator + aa + "_" + n + ".tif");
            n++;
//            IJ.log(wR + " " + hR);
        }
        
        for (Roi roiL : roiList) {
            inimg.setRoi(roiL);
            IJ.setForegroundColor(255, 255, 255);
            IJ.run(inimg, "Draw", "");
            inimg.updateAndDraw();
        }

    }

    public static String getFileExtension(String file_path) {
        String extension = "";

        int i = file_path.lastIndexOf('.');
        if (i >= 0) {
            extension = file_path.substring(i + 1);
        }

        return extension;
    }

    public static ImagePlus DuplicateImage(ImagePlus original) {
        if (original == null) {
            return null;
        }
        return (new Duplicator().run(original));
    }

}
