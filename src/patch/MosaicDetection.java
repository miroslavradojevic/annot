package patch;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.io.OpenDialog;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;

/**
 * Created by miroslav on 26-2-15.
 *
 * read mosaic or mosaics and extract the patches to classify later (in other plugin in this moment).
 * 
 * it can extract the patches with the choice GRID or LOCAL_MAXIMA (to do: RANDOM)
 * 
 * it can work with one or more images ("work_batch") (all of them in the same folder)
 * 
 *
 */
public class MosaicDetection implements PlugIn {

    ImagePlus inimg;
    String path, path1, images_path;
    int D, N;
    int W, H;
    double S; //sigma (radius) for Gaussian Blur
    int T; // tolerance for Local Maxima
    String ptch_distribution, batch;
    ArrayList<ImagePlus> all_images = new ArrayList<ImagePlus>();

    public void run(String s) {

        path = Prefs.get("annot.destination_folder", System.getProperty("user.home"));
        D = (int) Prefs.get("annot.D", 400); // annot. square size
        N = (int) Prefs.get("annot.N", 100);
        S = (double) Prefs.get("annot.S", 20);
        T = (int) Prefs.get("annot.T", 10);
        images_path = Prefs.get("annot.images_path", System.getProperty("user.home"));

        GenericDialog gdG = new GenericDialog("MosaicDetection");
        gdG.addStringField("destination_folder", path, 80);
        gdG.addNumericField("square_size", D, 0);
        gdG.addNumericField("nr_test_patches", N, 0);
        gdG.addChoice("patch_distribution", new String[]{"GRID", "RANDOM", "LOCAL_MAXIMA"}, "LOCAL_MAXIMA");
        gdG.addMessage("----------------------------");
        gdG.addNumericField("if Local Maxima, sigma", S, 0);
        gdG.addNumericField("if Local Maxima, tolerance", T, 0);
        gdG.addMessage("----------------------------");
        gdG.addChoice("work_batch", new String[]{"YES", "NO"}, "NO");
        gdG.addStringField("images_path", images_path, 80);

        gdG.showDialog();
        if (gdG.wasCanceled()) {
            return;
        }

        path = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("annot.destination_folder", path);
        D = (int) gdG.getNextNumber();
        Prefs.set("annot.D", D);
        N = (int) gdG.getNextNumber();
        Prefs.set("annot.N", N);
        ptch_distribution = gdG.getNextChoice();
        S = (double) gdG.getNextNumber();
        Prefs.set("annot.S", S);
        T = (int) gdG.getNextNumber();
        Prefs.set("annot.T", T);
        batch = gdG.getNextChoice();
        images_path = new File(gdG.getNextString()).getAbsolutePath();
        Prefs.set("annot.images_path", images_path);

        if (path.isEmpty()) {
            return;
        }
        if (batch.equals("NO")) {
            // select the image
            String in_folder = Prefs.get("id.folder", System.getProperty("user.home"));
            OpenDialog.setDefaultDirectory(in_folder);
            OpenDialog dc = new OpenDialog("Select file");
            in_folder = dc.getDirectory();
            String image_path = dc.getPath();
            if (image_path == null) {
                return;
            }
            Prefs.set("id.folder", in_folder);

            inimg = new ImagePlus(image_path);

            if (inimg == null) {
                return;
            }
            if (!Annotationer.getFileExtension(inimg.getTitle()).equalsIgnoreCase("TIF")) {
                IJ.log("open image with .TIF extension");
                return;
            }

            all_images.add(inimg);
        } else {
            all_images = getImagesFolder(images_path);
            System.out.println("There are " + String.valueOf(all_images.size())+" images.");
        }

        for (ImagePlus all_image : all_images) {
            inimg = all_image;
            File f = new File(path);
            if (!f.exists()) {
                f.mkdirs();
            }
            path1 = f.getAbsolutePath() + File.separator + inimg.getShortTitle() + "_testPatches"; //
            f = new File(path1);
            if (!f.exists()) {
                f.mkdirs();
            }
            inimg.show();
            W = inimg.getWidth();
            H = inimg.getHeight();
            Overlay ov = new Overlay();
            FileSaver fs;
            // check the sampling model
            if (ptch_distribution.equals("GRID")) { 

                int margin = D / 2;
                int step = (int) Math.floor(Math.sqrt(((W - D) * (H - D)) / N));

                int count = 0;

                for (int x = margin; x < W - margin - D; x += step) {
                    for (int y = margin; y < H - margin - D; y += step) {

                        Rectangle rec = new Rectangle(x, y, D, D);
                        Roi rec_roi = new Roi(rec);
                        ov.add(rec_roi);

                        inimg.setOverlay(ov);
                        inimg.updateAndDraw();

                        // extract the patch
                        ImageProcessor ipCopy = inimg.getChannelProcessor().duplicate();
                        ipCopy.setRoi(rec_roi);
                        ipCopy = ipCopy.crop();
                        ImagePlus impCopy = new ImagePlus("", ipCopy);
                        fs = new FileSaver(impCopy);
                        String filename = f.getAbsolutePath() + File.separator + inimg.getShortTitle()
                                + ",X,Y,D,i," + IJ.d2s(x, 0) + "," + IJ.d2s(y, 0) + "," + IJ.d2s(D, 0) + "," + IJ.d2s(count, 0) + ".tif";

                        fs.saveAsTiff(filename);

                        count++;
                        System.out.println(filename);

                    }
                }

            } else if (ptch_distribution.equals("RANDOM")) {

//            System.out.println("random...");
            } else if (ptch_distribution.equals("LOCAL_MAXIMA")) {

                ImagePlus imgGauss = inimg.duplicate();

                ArrayList<Point> lclPoints = new ArrayList<Point>(); //local maxima points
                IJ.run(imgGauss, "Gaussian Blur...", "sigma=" + S);
                IJ.run(imgGauss, "Find Maxima...", "noise=" + T + " output=List");
                ResultsTable rt = ResultsTable.getResultsTable();
                for (int i = 0; i < rt.getCounter(); i++) {
                    int x = (int) (rt.getValue("X", i));
                    int y = (int) (rt.getValue("Y", i));
                    lclPoints.add(new Point(x, y));
                }

                rt.reset();
                TextWindow tw = ResultsTable.getResultsWindow();
                tw.close();

//            System.out.println("done");
//            for (Point lclPoint : lclPoints) {
//                System.out.println("(x,y) " + lclPoint.getX() + "-" + lclPoint.getY());
//            }

                // list of points is extracted by this time

                int count = 0;
                for (Point lclPoint : lclPoints) {
                    if (((int) lclPoint.getX() + D) < W && ((int) lclPoint.getY() + D) < H && ((int) lclPoint.getX() - D / 2) >= 0 && ((int) lclPoint.getY() - D / 2) >= 0) {
                        int x = (int) lclPoint.getX() - D / 2;
                        int y = (int) lclPoint.getY() - D / 2;

                        Rectangle rec = new Rectangle(x, y, D, D);
                        Roi rec_roi = new Roi(rec);
                        ov.add(rec_roi);

                        inimg.setOverlay(ov);
                        inimg.updateAndDraw();

                        // extract the patch
                        ImageProcessor ipCopy = inimg.getChannelProcessor().duplicate();
                        ipCopy.setRoi(rec_roi);
                        ipCopy = ipCopy.crop();
                        ImagePlus impCopy = new ImagePlus("", ipCopy);
                        fs = new FileSaver(impCopy);
                        String filename = f.getAbsolutePath() + File.separator + inimg.getShortTitle()
                                + ",X,Y,D,i," + IJ.d2s((int) lclPoint.getX(), 0) + "," + IJ.d2s((int) lclPoint.getY(), 0) + "," + IJ.d2s(D, 0) + "," + IJ.d2s(count, 0) + ".tif";

                        fs.saveAsTiff(filename);
                        count++;
                        System.out.println(filename);
                    }
                }
                System.out.println("There are " + count + " patches.");
            }
            FileSaver fsMosaic = new FileSaver(inimg);
            String nameMosaic = f.getAbsolutePath() + File.separator + inimg.getShortTitle() + "_patches.tif";
            fsMosaic.saveAsTiff(nameMosaic);
            
        }
    }

    protected ArrayList<ImagePlus> getImagesFolder(String path) {
        ArrayList<ImagePlus> all_imag = new ArrayList<ImagePlus>();

        File directory = new File(path);
        String[] filesDir = directory.list();
        int nFiles = filesDir.length;
        if (nFiles != 0) {
            for (int i = 0; i < filesDir.length; i++) {
                if (filesDir[i].contains(".tif")) {
                    all_imag.add(new ImagePlus(path + File.separator + filesDir[i]));
                }
            }
        }
        return all_imag;
    }
}
