package patch;

import ij.*;
import ij.gui.*;
import ij.io.FileSaver;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
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
public class Annotationer implements PlugIn, MouseListener, MouseMotionListener, KeyListener, ImageListener {

    ImagePlus inimg;
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

    int currx, curry;

    public void run(String s) {

        NeuronList.clear();
        AstrocyteList.clear();
        BackList.clear();

        path = Prefs.get("annot.destination_folder", System.getProperty("user.home"));
        D = (int) Prefs.get("annot.D", 400); // annot. square size

        GenericDialog gdG = new GenericDialog("Annotationer");
        gdG.addStringField("destination_folder", path, 60);
        gdG.addNumericField("square_size", D, 0);

        gdG.showDialog();
        if (gdG.wasCanceled()) return;

        path = gdG.getNextString();//panelD.getTxtUrl();
        path = new File(path).getAbsolutePath();
        D = (int) gdG.getNextNumber();//panelD.getTxtWidth();

        if ((path.isEmpty())) {
            IJ.error("You have to write an url for the results and the number of groups that you need to work.");
            return;
        }

        Prefs.set("annot.destination_folder", path);
        Prefs.set("annot.D", D); // annot. square size

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

        if (inimg == null) return;
        if (!getFileExtension(inimg.getTitle()).equalsIgnoreCase("TIF")) {
            IJ.log("open image with .TIF extension");
            return;
        }

        inimg.show();
        canvas = inimg.getCanvas();
        canvas.removeKeyListener(IJ.getInstance());
        canvas.getImage().getWindow().removeKeyListener(IJ.getInstance());
        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);
        canvas.addKeyListener(this);
        canvas.getImage().getWindow().addKeyListener(this);
        ImagePlus.addImageListener(this);

        w = inimg.getWidth();
        h = inimg.getHeight();

        mask_obj = new byte[w * h];
        mask_temp = new byte[w * h];

        IJ.setTool("hand");

    }

    public void keyTyped(KeyEvent e) {

        if (e.getKeyChar()=='n') {
            append(currx, curry, NeuronList, Color.RED);
            print();
        }
        if (e.getKeyChar()=='a') {
            append(currx, curry, AstrocyteList, Color.MAGENTA);
            print();
        }
        if (e.getKeyChar()=='d') {
            remove(currx, curry);
            print();
        }
        if (e.getKeyChar()=='b') {

            N = (int) Prefs.get("annot.N", 30);

            GenericDialog gd = new GenericDialog("Background patches");
            gd.addNumericField("nr_background_patches", N, 0);
            gd.showDialog();
            if (gd.wasCanceled()) return;
            N = (int) gd.getNextNumber();
            Prefs.set("annot.N", N);

            generateObjectMap();
            randomBackground();
            print();

        }
        if (e.getKeyChar()=='e') {

            GenericDialog gd = new GenericDialog("Export?");
            gd.showDialog();
            if (gd.wasCanceled()) return;

            exportPatches();
            exportAnnots();

        }

        if (e.getKeyChar()=='+') canvas.zoomIn(currx, curry);
        if (e.getKeyChar()=='-') canvas.zoomOut(currx, curry);

    }

    private void randomBackground() {
        Random r = new Random();
        int count = 0;
        int xR;
        int yR;

        byte[] uptillnow = new byte[mask_temp.length];

        System.out.print("generating background patches");

        while (count < N) {

            System.out.print(".");

            xR = r.nextInt(w-D)+D/2; // simulate mouse pinpointing
            yR = r.nextInt(h-D)+D/2;

            int xRect = Math.round(xR - D / 2f);
            int yRect = Math.round(yR - D / 2f);

                // fill the mask up
                Arrays.fill(mask_temp, (byte) 0);
                int cnt = 0;
                int cnt_wrt_objects = 0;
                for (int xloop = xRect; xloop < xRect + D; xloop++) {
                    for (int yloop = yRect; yloop < yRect + D; yloop++) {
                        mask_temp[yloop * w + xloop] = (byte) 255;
                        if ((uptillnow[yloop * w + xloop] & 0xff) > 0 ) {
                            cnt++;
                        }
                        if ( (mask_obj[yloop * w + xloop] & 0xff) > 0) {
                            cnt_wrt_objects++;
                        }

                    }
                }

                if (cnt==0 && cnt_wrt_objects==0) { // (cnt / (float) (D * D)) < 0.1

                    logic_or(uptillnow, mask_temp);

                    append(xR, yR, BackList, Color.BLUE);

                    count++;

                }

        }

        System.out.println();
//        new ImagePlus("uptillnow", new ByteProcessor(w, h, uptillnow)).show();
    }

    private void exportAnnots(){

        System.out.println("export annots...");

        String det_path = path + File.separator + "ann_" + inimg.getShortTitle() + ".log";

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
            logWriter.println("NEURON   \t" +      IJ.d2s(NeuronList.get(k).getX(),0) + "\t" +  IJ.d2s(NeuronList.get(k).getY(),0) + "\t" +     IJ.d2s(NeuronList.get(k).getWidth(),0) + "\t" +     IJ.d2s(NeuronList.get(k).getHeight(),0));

        for (int k = 0; k < AstrocyteList.size(); k++)
            logWriter.println("ASTROCYTE\t" +   IJ.d2s(AstrocyteList.get(k).getX(),0) + "\t" +  IJ.d2s(AstrocyteList.get(k).getY(),0) + "\t" +  IJ.d2s(AstrocyteList.get(k).getWidth(),0) + "\t" +  IJ.d2s(AstrocyteList.get(k).getHeight(),0));

        for (int k = 0; k < BackList.size(); k++)
            logWriter.println("BACKGROUD\t" +   IJ.d2s(BackList.get(k).getX(),0) + "\t" +       IJ.d2s(BackList.get(k).getY(),0) + "\t" +       IJ.d2s(BackList.get(k).getWidth(),0) + "\t" +       IJ.d2s(BackList.get(k).getHeight(),0));

        logWriter.close();
        System.out.println("done. "+det_path);

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
        currx = canvas.offScreenX(e.getX());
        curry = canvas.offScreenY(e.getY());
        PolygonRoi pr = new PolygonRoi(
                new float[]{currx - .5f * D, currx - .5f * D, currx + .5f * D, currx + .5f * D},
                new float[]{curry - .5f * D, curry + .5f * D, curry + .5f * D, curry - .5f * D},
                4,
                PolygonRoi.POLYGON
        );
        pr.setFillColor(new Color(1, 1, 0, 0.2f));
        if (floating_ovl.size() > 0) floating_ovl.remove(floating_ovl.size() - 1);
        floating_ovl.add(pr);
        canvas.setOverlay(floating_ovl);
        inimg.updateAndDraw();
    }

    private void append(int x, int y, ArrayList<Rectangle> destination, Color col) {

        int xRect = Math.round(x - D / 2f);
        int yRect = Math.round(y - D / 2f);

        if ((xRect < 0) || (yRect < 0)) return;

        Rectangle rec = new Rectangle(xRect, yRect, D, D);

        // check if it overlaps with one of the current background patches
        boolean overlaps_with_back = false; // useful if we got some back patches annotated
        for (int k = 0; k < BackList.size(); k++) {
            if (overlap(rec, BackList.get(k))>0) {
                overlaps_with_back = true;
                break;
            }
        }

        if (overlaps_with_back) return;

        destination.add(rec);

        Roi roi = new Roi(rec); // turn into ij roi that can be plotted
        roi.setStrokeColor(col);
        Roi last = floating_ovl.get(floating_ovl.size() - 1);
        floating_ovl.remove(floating_ovl.size() - 1);
        floating_ovl.add(roi);
        floating_ovl.add(last); // keep the floating square on top

        inimg.setOverlay(floating_ovl);
        inimg.updateAndDraw();

    }

    private void remove(int x, int y) {

        int xRect = Math.round(x - D / 2);
        int yRect = Math.round(y - D / 2);

        if ((xRect < 0) || (yRect < 0)) return;

        Rectangle rec = new Rectangle(xRect, yRect, D, D);

        // remove from the overlay, then  remove from the rectangle list (sequence is important here)

        for (int k = 0; k < NeuronList.size(); k++) {
            if (overlap(rec, NeuronList.get(k))>0) {
                floating_ovl.remove(new Roi(NeuronList.get(k)));
                NeuronList.remove(k);
            }
        }

        for (int k = 0; k < AstrocyteList.size(); k++) {
            if (overlap(rec, AstrocyteList.get(k))>0) {
                floating_ovl.remove(new Roi(AstrocyteList.get(k)));
                AstrocyteList.remove(k);
            }
        }

        for (int k = 0; k < BackList.size(); k++) {
            if (overlap(rec, BackList.get(k))>0) {
                floating_ovl.remove(new Roi(BackList.get(k)));
                BackList.remove(k);
            }
        }

        inimg.setOverlay(floating_ovl);
        inimg.updateAndDraw();

    }

    private void generateObjectMap() {

        Arrays.fill(mask_obj, (byte) 0);

        for (int k = 0; k < NeuronList.size(); k++) {

            int xr = (int) Math.round(NeuronList.get(k).getX());
            int yr = (int) Math.round(NeuronList.get(k).getY());

            for (int xloop = xr; xloop < xr + D; xloop++) {
                for (int yloop = yr; yloop < yr + D; yloop++) {
                    mask_obj[yloop * w + xloop] = (byte) 255;
                }
            }
        }

        for (int k = 0; k < AstrocyteList.size(); k++) {

            int xr = (int) Math.round(AstrocyteList.get(k).getX());
            int yr = (int) Math.round(AstrocyteList.get(k).getY());

            for (int xloop = xr; xloop < xr + D; xloop++) {
                for (int yloop = yr; yloop < yr + D; yloop++) {
                    mask_obj[yloop * w + xloop] = (byte) 255;
                }
            }
        }

    }

    private void print() {
        System.out.println("#patches:\t" + NeuronList.size() + " neuron,\t" + AstrocyteList.size() + " astrocyte,\t" + BackList.size() + " background.");
    }

    private void exportPatches() {

        System.out.println("export patches...");

        File f;
        FileSaver fs = null;
        String filename;
        String name;
        ImageStack collection;

        /*

         */
        name = "neuron";
        f = new File(path+ File.separator + name);
        f.mkdirs();
        for(File file: f.listFiles()) file.delete();

        collection = new ImageStack(D,D);

        for (int k = 0; k < NeuronList.size(); k++) {

            ImageProcessor ipCopy = inimg.getChannelProcessor().duplicate();
            ipCopy.setRoi(new Roi(NeuronList.get(k)));
            ipCopy = ipCopy.crop();
            ImagePlus impCopy = new ImagePlus("", ipCopy);
            fs = new FileSaver(impCopy);
            filename = f.getAbsolutePath() + File.separator + name + "_" + String.format("%03d", k) + ".tif";
            fs.saveAsTiff(filename);
            System.out.println(filename);
            collection.addSlice(name + "_" + String.format("%03d",k) + "("+NeuronList.get(k).getX()+","+NeuronList.get(k).getX()+")", impCopy.getProcessor());
        }

        if (NeuronList.size()>0) {
            fs = new FileSaver(new ImagePlus(name, collection));
            filename = path+ File.separator+name+".tif";
            System.out.println(filename);
            if (NeuronList.size()>1) fs.saveAsTiffStack(filename); else fs.saveAsTiff(filename);
        }



        name = "astrocytes";
        f = new File(path+ File.separator + name);
        f.mkdirs();
        for(File file: f.listFiles()) file.delete();

        collection = new ImageStack(D,D);

        for (int k = 0; k < AstrocyteList.size(); k++) {

            ImageProcessor ipCopy = inimg.getChannelProcessor().duplicate();
            ipCopy.setRoi(new Roi(AstrocyteList.get(k)));
            ipCopy = ipCopy.crop();
            ImagePlus impCopy = new ImagePlus("", ipCopy);
            fs = new FileSaver(impCopy);
            filename = f.getAbsolutePath() + File.separator + name + "_" + String.format("%03d", k) + ".tif";
            fs.saveAsTiff(filename);
            System.out.println(filename);
            collection.addSlice(name + "_" + String.format("%03d",k) + "("+AstrocyteList.get(k).getX()+","+AstrocyteList.get(k).getX()+")", impCopy.getProcessor());
        }

        if (AstrocyteList.size()>0) {
            fs = new FileSaver(new ImagePlus(name, collection));
            filename = path+ File.separator+name+".tif";
            System.out.println(filename);
            if (AstrocyteList.size()>1) fs.saveAsTiffStack(filename); else fs.saveAsTiff(filename);
        }



        name = "background";
        f = new File(path+ File.separator + name);
        f.mkdirs();
        for(File file: f.listFiles()) file.delete();

        collection = new ImageStack(D,D);

        for (int k = 0; k < BackList.size(); k++) {

            ImageProcessor ipCopy = inimg.getChannelProcessor().duplicate();
            ipCopy.setRoi(new Roi(BackList.get(k)));
            ipCopy = ipCopy.crop();
            ImagePlus impCopy = new ImagePlus("", ipCopy);
            fs = new FileSaver(impCopy);
            filename = f.getAbsolutePath() + File.separator + name + "_" + String.format("%03d", k) + ".tif";
            fs.saveAsTiff(filename);
            System.out.println(filename);
            collection.addSlice(name + "_" + String.format("%03d",k) + "("+BackList.get(k).getX()+","+BackList.get(k).getX()+")", impCopy.getProcessor());
        }

        if (BackList.size()>0){
            fs = new FileSaver(new ImagePlus(name, collection));
            filename = path+ File.separator+name+".tif";
            System.out.println(filename);
            if (BackList.size()>1) fs.saveAsTiffStack(filename); else fs.saveAsTiff(filename);
        }

        String ann_path = path + File.separator + "ann_" + inimg.getShortTitle() + ".tif";
        fs = new FileSaver(inimg);
        fs.saveAsTiff(ann_path);

        System.out.println("done. " + ann_path);

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

    public void imageClosed(ImagePlus imagePlus) {


        if (canvas.getImage().getWindow()!=null)
            canvas.getImage().getWindow().removeKeyListener(this);
        if (canvas!=null)
            canvas.removeKeyListener(this);
        ImagePlus.removeImageListener(this);

        GenericDialog gd = new GenericDialog("Wanna Export?");
        gd.showDialog();
        if (gd.wasCanceled()) return;
        exportPatches();
        exportAnnots();

    }

    /////////////////////////////////////////////
    public void mouseClicked(MouseEvent e) {}

    public void mousePressed(MouseEvent e) {}

    public void mouseReleased(MouseEvent e) {}

    public void mouseEntered(MouseEvent e) {}

    public void mouseExited(MouseEvent e) {}

    public void mouseDragged(MouseEvent e) {}

    public void keyPressed(KeyEvent e) {}

    public void keyReleased(KeyEvent e) {}

    public void imageOpened(ImagePlus imagePlus) {}

    public void imageUpdated(ImagePlus imagePlus) {}
}
