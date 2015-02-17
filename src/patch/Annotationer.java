package patch;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.*;
import ij.io.FileSaver;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * Created by miroslav on 9-2-15.
 */
public class Annotationer implements PlugIn, MouseListener, MouseMotionListener {

    ImagePlus inimg;
    String[] namesGroups = {"Neuron", "Astrocyte", "Stop", "Cancel"};
    int i, j, b = 0;
    String image_path;
    String path;
    ImageCanvas canvas;
    Overlay floating_ovl = new Overlay();

    int D, N;
    int w, h;

    ArrayList<Rectangle> NeuronList = new ArrayList<Rectangle>();
    ArrayList<Rectangle> AstrocyteList = new ArrayList<Rectangle>();
    ArrayList<Rectangle> BackList = new ArrayList<Rectangle>();

    byte[] mask_obj; // will contain neurons and astrocytes tags
    byte[] mask_temp; // will be assigned each time a new (random) was created

    public void run(String s) {

        NeuronList.clear();
        AstrocyteList.clear();
        BackList.clear();

        path = Prefs.get("annot.destination_folder", System.getProperty("user.home"));
        D = (int) Prefs.get("annot.D", 400); // annot. square size
        N = (int) Prefs.get("annot.N", 50); // number of random backr. patches

        GenericDialog gdG = new GenericDialog("Annotationer");
        gdG.addStringField("destination_folder", path, 60);
        gdG.addNumericField("square_size", D, 0);
        gdG.addNumericField("nr_background_patches", N, 0);
        gdG.showDialog();
        if (gdG.wasCanceled()) return;

        path = gdG.getNextString();//panelD.getTxtUrl();
        D = (int) gdG.getNextNumber();//panelD.getTxtWidth();
        N = (int) gdG.getNextNumber();//panelD.getTxtNBackG();

        if ((path.isEmpty())) {
            IJ.error("You have to write an url for the results and the number of groups that you need to work.");
            return;
        }

        Prefs.set("annot.destination_folder", path);
        Prefs.set("annot.D", D); // annot. square size
        Prefs.set("annot.N", N); // number of random backr. patches

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

        inimg.show();
        canvas = inimg.getCanvas();
        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);

        w = inimg.getWidth();
        h = inimg.getHeight();

        mask_obj = new byte[w * h];
        mask_temp = new byte[w * h];

        IJ.setTool("hand");

    }

    private void randomBackground() {
        Random r = new Random();
        int count = 0;
        int xR;
        int yR;

        byte[] uptillnow = new byte[mask_temp.length];

        while (count < N) {

            xR = r.nextInt(w);
            yR = r.nextInt(h);

            if ((xR + D < w) && (yR + D < h)) {
                // fill the mask up
                Arrays.fill(mask_temp, (byte) 0);
                int cnt = 0;
                int cnt_wrt_objects = 0;
                for (int xloop = xR; xloop < xR + D; xloop++) {
                    for (int yloop = yR; yloop < yR + D; yloop++) {
                        mask_temp[yloop * w + xloop] = (byte) 255;
                        if ((uptillnow[yloop * w + xloop] & 0xff) > 0 ) {
                            cnt++;
                        }
                        if ( (mask_obj[yloop * w + xloop] & 0xff) > 0) {
                            cnt_wrt_objects++;
                        }

                    }
                }

                if ((cnt / (float) (D * D)) < 0.1 && cnt_wrt_objects==0) {

                    //                new ImagePlus("before", new ByteProcessor(w,h,mask_temp)).show();
                    logic_or(uptillnow, mask_temp);

//                new ImagePlus("after", new ByteProcessor(w,h,mask_temp)).show();
//                    for (int k = 0; k < uptillnow.length; k++) {
//                        uptillnow[k] = mask_temp[k];
//                    }

                    Rectangle rectRand = new Rectangle(xR, yR, D, D);
                    BackList.add(rectRand);
                    Roi roiRand = new Roi(rectRand);
                    inimg.setRoi(roiRand);
//                    System.out.println("" + count);
                    roiRand.setStrokeColor(Color.blue);
                    Roi last = floating_ovl.get(floating_ovl.size() - 1);
                    floating_ovl.remove(floating_ovl.size() - 1);
                    floating_ovl.add(roiRand);
                    floating_ovl.add(last);

                    inimg.setOverlay(floating_ovl);
                    inimg.updateAndDraw();

                    //to save the images
                    ImageProcessor ip = canvas.getImage().getChannelProcessor();
                    ImageProcessor ipCopy = ip.duplicate();
                    ipCopy.setRoi(roiRand);
                    ipCopy = ipCopy.crop();
                    ImagePlus impCopy = new ImagePlus("Background", ipCopy);
                    String auxPath = path + File.separator + "Background";
                    File url = new File(auxPath);
                    url.mkdirs();
                    new FileSaver(impCopy).saveAsTiff(auxPath + File.separator + "Background" + "_" + b + ".tif");
                    b++;

                    count++;

                }

            }
        }

//        new ImagePlus("aaa", new ByteProcessor(w, h, uptillnow)).show();
    }

    private void exportAnnots(){
        String det_path = path + File.separator + "annots_" + inimg.getShortTitle() + ".log";

        PrintWriter logWriter = null;
        try {
            logWriter = new PrintWriter(det_path);
            logWriter.print("");
            logWriter.close();
        } catch (FileNotFoundException ex) {}

        try {
            logWriter = new PrintWriter(new BufferedWriter(new FileWriter(det_path, true)));
            logWriter.println("# annotation output: TYPE x y w h");
        } catch (IOException e) {}

        for (int k = 0; k < NeuronList.size(); k++)
            logWriter.println("NEURON\t" + NeuronList.get(k).getX() + "\t" + NeuronList.get(k).getY() + "\t" + NeuronList.get(k).getWidth() + "\t" + NeuronList.get(k).getHeight());

        for (int k = 0; k < NeuronList.size(); k++)
            logWriter.println("ASTROCYTE\t" + AstrocyteList.get(k).getX() + "\t" + AstrocyteList.get(k).getY() + "\t" + AstrocyteList.get(k).getWidth() + "\t" + AstrocyteList.get(k).getHeight());

        for (int k = 0; k < NeuronList.size(); k++)
            logWriter.println("ASTROCYTE\t" + BackList.get(k).getX() + "\t" + BackList.get(k).getY() + "\t" + BackList.get(k).getWidth() + "\t" + BackList.get(k).getHeight());

        logWriter.close();
        System.out.println("done! "+det_path);

    }

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

    public void mouseMoved(MouseEvent e) {
        float x = canvas.offScreenX(e.getX());
        float y = canvas.offScreenY(e.getY());
        PolygonRoi pr = new PolygonRoi(new float[]{x - .5f * D, x - .5f * D, x + .5f * D, x + .5f * D}, new float[]{y - .5f * D, y + .5f * D, y + .5f * D, y - .5f * D}, 4, PolygonRoi.POLYGON);
        pr.setFillColor(new Color(1, 1, 0, 0.1f));
        if (floating_ovl.size() > 0) {
            floating_ovl.remove(floating_ovl.size() - 1);
        }
        floating_ovl.add(pr);
        canvas.setOverlay(floating_ovl);
        inimg.updateAndDraw();
    }

    public void mouseClicked(MouseEvent e) {

        float x = canvas.offScreenX(e.getX());
        float y = canvas.offScreenY(e.getY());

        GenericDialog gd = new GenericDialog("CHOOSE...");
        gd.addChoice("choose ", namesGroups, namesGroups[0]);
        gd.showDialog();
        if (gd.wasCanceled()) {


            return;
        }
        String aa = gd.getNextChoice();
        int xRect = (int) (x - D / 2);
        int yRect = (int) (y - D / 2);
        if ((xRect < 0) || (yRect < 0)) {
            return;
        }

        if (aa.equals(namesGroups[0])) {

            Rectangle rect1 = new Rectangle(xRect, yRect, (int) D, (int) D);
            NeuronList.add(rect1);

            // update the map
            for (int xloop = xRect; xloop < xRect + D; xloop++) {
                for (int yloop = yRect; yloop < yRect + D; yloop++) {
                    mask_obj[yloop * w + xloop] = (byte) 255;
                }
            }

            // inteface thngy
            Roi roi = new Roi(rect1);
            roi.setStrokeColor(Color.red);
            Roi last = floating_ovl.get(floating_ovl.size() - 1);
            floating_ovl.remove(floating_ovl.size() - 1);
            floating_ovl.add(roi);
            floating_ovl.add(last);

            inimg.setOverlay(floating_ovl);
            inimg.updateAndDraw();

            //to save the images
            ImageProcessor ip = canvas.getImage().getChannelProcessor();
            ImageProcessor ipCopy = ip.duplicate();
            ipCopy.setRoi(roi);
            ipCopy = ipCopy.crop();
            ImagePlus impCopy = new ImagePlus(aa, ipCopy);
            String auxPath = path + File.separator + aa;
            File url = new File(auxPath);
            url.mkdirs();
            new FileSaver(impCopy).saveAsTiff(auxPath + File.separator + aa + "_" + i + ".tif");
            i++;

        } else if (aa.equals(namesGroups[1])) {
            Rectangle rect1 = new Rectangle(xRect, yRect, (int) D, (int) D);
            AstrocyteList.add(rect1);
            // update the map
            for (int xloop = xRect; xloop < xRect + D; xloop++) {
                for (int yloop = yRect; yloop < yRect + D; yloop++) {
                    mask_obj[yloop * w + xloop] = (byte) 255;
                }
            }

            Roi roi = new Roi(rect1);
            roi.setStrokeColor(Color.MAGENTA);
            Roi last = floating_ovl.get(floating_ovl.size() - 1);
            floating_ovl.remove(floating_ovl.size() - 1);
            floating_ovl.add(roi);
            floating_ovl.add(last);

            inimg.setOverlay(floating_ovl);
            inimg.updateAndDraw();
            //to save the images
            ImageProcessor ip = canvas.getImage().getChannelProcessor();
            ImageProcessor ipCopy = ip.duplicate();
            ipCopy.setRoi(roi);
            ipCopy = ipCopy.crop();
            ImagePlus impCopy = new ImagePlus(aa, ipCopy);
            String auxPath = path + File.separator + aa;
            File url = new File(auxPath);
            url.mkdirs();
            new FileSaver(impCopy).saveAsTiff(auxPath + File.separator + aa + "_" + j + ".tif");
            j++;
        }
        else if(aa.equals(namesGroups[2]))
        {
            randomBackground();
            System.out.println(NeuronList.size() + " neurons");
            System.out.println(AstrocyteList.size() + " astros");
            System.out.println(BackList.size() + " backs");
            canvas.removeMouseListener(this);
            System.out.println("saving... \t" + path + File.separator + "annot_" + inimg.getShortTitle() + ".tif");
            IJ.saveAs("Tiff", path + File.separator + "annot_" + inimg.getShortTitle() + ".tif");
            exportAnnots();
        }
        else if(aa.equals(namesGroups[3])) {
            System.out.println("cancelling...");
        }
    }

    private void logic_or(byte[] a, byte[] b) { // final output is stored in a
        for (int k = 0; k < a.length; k++) {
            a[k] = ((a[k] & 0xff) > (b[k] & 0xff)) ? a[k] : b[k];
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

    /////////////////////////////////////////////
    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    public void mouseDragged(MouseEvent e) {
    }


}
