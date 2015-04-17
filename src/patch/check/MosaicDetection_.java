/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package patch.check;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.io.OpenDialog;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.text.TextWindow;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import patch.Annotationer;

/**
 * Created by miroslav on 26-2-15.
 *
 * read mosaic or mosaics and extract the patches to classify later (in other
 * plugin in this moment).
 *
 * it can extract the patches with the choice GRID or LOCAL_MAXIMA (to do:
 * RANDOM)
 *
 * it can work with one or more images ("work_batch") (all of them in the same
 * folder)
 *
 */
public class MosaicDetection_ implements PlugIn {

    ImagePlus inimg;
    String path, path1, images_path;
    int D, N;
    int W, H;
    double S; //sigma (radius) for Gaussian Blur
    int T; // tolerance for Local Maxima
    String ptch_distribution, batch;
    ArrayList<ImagePlus> all_images = new ArrayList<ImagePlus>();
    Map<Point, ArrayList<Point>> map = new HashMap<Point, ArrayList<Point>>();
    int percentOver = 20;

    public void run(String s) {

        //-----DIALOG-----
        path = Prefs.get("annot.destination_folder", System.getProperty("user.home"));
        D = (int) Prefs.get("annot.D", 400); //square size
        N = (int) Prefs.get("annot.N", 100); //number of patches
        S = (double) Prefs.get("annot.S", 20);//if lclMax, sigma
        T = (int) Prefs.get("annot.T", 10);// if lclMax, tolerance
        percentOver = (int) Prefs.get("annot.percentOver", 40);// %, that is neccessary, to consider if a square belong to a class or not
        images_path = Prefs.get("annot.images_path", System.getProperty("user.home"));//iff it works on batch, then this is the folder with all images

        GenericDialog gdG = new GenericDialog("MosaicDetection");
        gdG.addStringField("destination_folder", path, 80);
        gdG.addNumericField("square_size", D, 0);
        gdG.addNumericField("nr_test_patches", N, 0);
        gdG.addChoice("patch_distribution", new String[]{"GRID", "RANDOM", "LOCAL_MAXIMA"}, "LOCAL_MAXIMA");
        gdG.addMessage("----If Local Maxima Method----");
        gdG.addNumericField("sigma", S, 0);
        gdG.addNumericField("tolerance", T, 0);
        gdG.addNumericField("% overloapped", percentOver, 0);
        gdG.addMessage("----Studing several images----");
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
        percentOver = (int) gdG.getNextNumber();
        Prefs.set("annot.percentOver", percentOver);
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
            System.out.println("There are " + String.valueOf(all_images.size()) + " images.");
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
            inimg.show(); //opening the image to work with it
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
//                        IJ.run(impCopy,"8-bit", "");
                        fs = new FileSaver(impCopy);
                        String filename = f.getAbsolutePath() + File.separator + inimg.getShortTitle()
                                + ",X,Y,D,i," + IJ.d2s(x, 0) + "," + IJ.d2s(y, 0) + "," + IJ.d2s(D, 0) + "," + IJ.d2s(count, 0) + ".tif";

                        fs.saveAsTiff(filename);

                        count++;
                        System.out.println(filename);

                    }
                }

            } else if (ptch_distribution.equals("RANDOM")) {
              //TO DO   
            //System.out.println("random...");
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
                //to save the local maxima points like a rois in a zip-file
                RoiManager rm = RoiManager.getInstance();
                if (rm == null) {
                    rm = new RoiManager();
                }

                for (Point lclPoint : lclPoints) {
                    Roi roi = new Roi((int) lclPoint.getX(), (int) lclPoint.getY(), 1, 1);
                    rm.addRoi(roi);
                }

                rm.runCommand("Save", f.getAbsolutePath() + File.separator + inimg.getShortTitle() + "_lclMaxPoints" + ".zip");
                rm.close();

                // table with colors. They are irrelevants, they don't mean special anything, it uses them to see better
                Color[] c = new Color[lclPoints.size()];
                for (int i = 0; i < lclPoints.size(); i++) {
                    c[i] = getRandomColor();
                }

                int count = 0;
                RoiManager rmm = RoiManager.getInstance();
                if (rmm == null) {
                    rmm = new RoiManager();
                }
                
                //map stores in the key set some local maxima points and its second part, it stores the local maxima points which are overlapping with the a point which is key in that element of the map
                
                for (int i = 0; i < lclPoints.size(); i++) {
                    if (((int) lclPoints.get(i).getX() + D / 2) < W && ((int) lclPoints.get(i).getY() + D / 2) < H && ((int) lclPoints.get(i).getX() - D / 2) >= 0 && ((int) lclPoints.get(i).getY() - D / 2) >= 0) {

                        Set<Point> keySet = map.keySet();
                        ArrayList<Point> keyList = new ArrayList<Point>(keySet);
                        boolean is = false;
                        //first time
                        if (keyList.isEmpty()) {
                            map.put(lclPoints.get(i), new ArrayList<Point>());
                            int x = (int) lclPoints.get(i).getX() - D / 2;
                            int y = (int) lclPoints.get(i).getY() - D / 2;
                            Rectangle rec = new Rectangle(x, y, D, D);
                            Roi rec_roi = new Roi(rec);
                            rec_roi.setStrokeColor(c[i]);
                            rec_roi.setName(Integer.toString(i));
                            ov.add(rec_roi);

                            rmm.addRoi(rec_roi);

                            inimg.setOverlay(ov);
                            inimg.updateAndDraw();
                            // extract the patch
                            ImageProcessor ipCopy = inimg.getChannelProcessor().duplicate();

                            ipCopy.setRoi(rec_roi);
                            ipCopy = ipCopy.crop();
                            ImagePlus impCopy = new ImagePlus("", ipCopy);
                            fs = new FileSaver(impCopy);
                            String filename = f.getAbsolutePath() + File.separator + inimg.getShortTitle()
                                    + ",X,Y,D,i," + IJ.d2s((int) x, 0) + "," + IJ.d2s((int) y, 0) + "," + IJ.d2s(D, 0) + "," + IJ.d2s(count, 0) + ".tif";

                            fs.saveAsTiff(filename);
                            count++;
                            System.out.println(filename);
                        } else {
                            //other times, it checks if the square from the point, is overlapping with other studied square (point). 
                            int j = 0;
                            while (j < keyList.size() && !is) {
//                            for (int j = 0; j < keyList.size(); j++) {
                                Point p = keyList.get(j);
                                int x1 = (int) p.getX() - D / 2;
                                int y1 = (int) p.getY() - D / 2;
                                Rectangle rec1 = new Rectangle(x1, y1, D, D);

                                int x2 = (int) lclPoints.get(i).getX() - D / 2;
                                int y2 = (int) lclPoints.get(i).getY() - D / 2;
                                Rectangle rec2 = new Rectangle(x2, y2, D, D);

                                double over = overlap(rec1, rec2);
                                double percent = over / (D * D) * 100;

                                if (percent > percentOver) {
                                    System.out.println("percentage: " + percent);
                                    ArrayList<Point> aux = map.get(p);
                                    aux.add(lclPoints.get(i));
                                    map.put(p, aux);
                                    is = true;
                                }
                                j++;
                            }
                            if (!is) { //if the "new" square is overlapping with a studied square, then it is stored in the list, which has the key, is the point with it is overlapping
                                map.put(lclPoints.get(i), new ArrayList<Point>());
                                int x = (int) lclPoints.get(i).getX() - D / 2;
                                int y = (int) lclPoints.get(i).getY() - D / 2;
                                Rectangle rec = new Rectangle(x, y, D, D);
                                Roi rec_roi = new Roi(rec);
                                rec_roi.setStrokeColor(c[i]);
                                rec_roi.setName(Integer.toString(i));
                                ov.add(rec_roi);

                                rmm.addRoi(rec_roi);

                                inimg.setOverlay(ov);
                                inimg.updateAndDraw();
                                // extract the patch
                                ImageProcessor ipCopy = inimg.getChannelProcessor().duplicate();
                                ipCopy.setRoi(rec_roi);
                                ipCopy = ipCopy.crop();
                                ImagePlus impCopy = new ImagePlus("", ipCopy);
                                fs = new FileSaver(impCopy);
                                String filename = f.getAbsolutePath() + File.separator + inimg.getShortTitle()
                                        + ",X,Y,D,i," + IJ.d2s((int) x, 0) + "," + IJ.d2s((int) y, 0) + "," + IJ.d2s(D, 0) + "," + IJ.d2s(count, 0) + ".tif";
                                fs.saveAsTiff(filename);
                                count++;
                                System.out.println(filename);
                            }
                        }
                    }
                }
                //to stored the selected squares in a zip-file.
                rmm.runCommand("Save", f.getAbsolutePath() + File.separator + inimg.getShortTitle() + "_Rectangles" + ".zip");
                System.out.println("There are " + count + " patches.");
                //to be able to work in WNDCHRM, it's necessary that the images are in 8bits, so with this method it converts all selected patches
                to8bits(f.getAbsolutePath());
            }
            //to save the full image with the selection of squares.
            FileSaver fsMosaic = new FileSaver(inimg);
            String nameMosaic = f.getAbsolutePath() + File.separator + inimg.getShortTitle() + "_patches.tif";
            fsMosaic.saveAsTiff(nameMosaic);
        }
        
    }

    //to get the images in a folder.
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

    public void to8bits(String path) {
        if (path.isEmpty()) {
            return;
        }
        FileSaver fs;

        File directory = new File(path);
        String[] filesDir = directory.list();
        int nFiles = filesDir.length;
        for (int i = 0; i < nFiles; i++) {
            if (filesDir[i].contains(".tif")) {
                ImagePlus imp = new ImagePlus(path + "\\" + filesDir[i]);
                IJ.run(imp, "8-bit", "");
                fs = new FileSaver(imp);
                String filename = path + "\\" + filesDir[i];
                fs.saveAsTiff(filename);
            }
        }
    }

    private static Color getRandomColor() {
        Random random = new Random();
        final float hue = random.nextFloat();
        final float saturation = 0.9f;//1.0 for brilliant, 0.0 for dull
        final float luminance = 1.0f; //1.0 for brighter, 0.0 for black
        Color color = Color.getHSBColor(hue, saturation, luminance);
        return color;
    }

    //to study of overlapping the two rectangles
    private double overlap(Rectangle rect1, Rectangle rect2) {

        double x11 = rect1.getX();//left -up
        double y11 = rect1.getY();//left -up

        double x12 = rect1.getX() + rect1.getWidth();//right -up

        double y12 = rect1.getY() + rect1.getHeight();//left-down

        double x21 = rect2.getX();//left -up
        double y21 = rect2.getY();//left -up

        double x22 = rect2.getX() + rect2.getWidth();//right -up

        double y22 = rect2.getY() + rect2.getHeight();//left-down

        double xDiff;
        if (x11 < x21) {
            xDiff = x12 - x21;
        } else {
            xDiff = x22 - x11;
        }

        double yDiff;
        if (y11 < y21) {
            yDiff = y12 - y21;
        } else {
            yDiff = y22 - y11;
        }
        xDiff = (xDiff < 0) ? 0 : xDiff;
        yDiff = (yDiff < 0) ? 0 : yDiff;

        return xDiff * yDiff;
    }

}
