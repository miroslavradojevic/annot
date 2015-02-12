package patch;

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

/**
 * Created by miroslav on 9-2-15.
 */
public class Annotationer implements PlugIn, MouseListener, MouseMotionListener, KeyListener, ImageListener {

    ImagePlus inimg;

    int i, j = 0;

    Setting settings;
    String image_path;
    ImageCanvas canvas;
    ImageWindow wind;
    String[] namesGroups;
    Overlay ov_rectangle = new Overlay();

    float x1, y1, x2, y2;

    //there are 7 different colors
    private static Color[] ColorArray = {new Color(1, 1, 0, 0.25f), new Color(1, 0, 1, 0.25f), new Color(1, 1, 1, 0.25f), new Color(1, 0, 0, 0.25f), new Color(0, 1, 0, 0.25f), new Color(0, 0, 1, 0.25f), new Color(0.96f, 0, 0.52f, 0.25f)};

    public void imageOpened(ImagePlus imagePlus) {

    }

    public void imageClosed(ImagePlus imagePlus) {

    }

    public void imageUpdated(ImagePlus imagePlus) {

    }

    public void keyTyped(KeyEvent e) {

    }

    public void keyPressed(KeyEvent e) {

    }

    public void keyReleased(KeyEvent e) {

    }

    public void mouseClicked(MouseEvent e) {

        System.out.println("clicked");
//        System.out.println("clicked");
//        ov_rectangle.clear();
//        IJ.log("aaa");

    }

    public void mousePressed(MouseEvent e) {

        if (IJ.getToolName().equals("rectangle")) {
            x1 = canvas.offScreenX(e.getX());
            y1 = canvas.offScreenY(e.getY());
//        System.out.println("pressed " + x1 + " , " + y1);
//        x1 = 	canvas.offScreenX(e.getX());
//        y1 = 	canvas.offScreenY(e.getY());
//
//        IJ.log("pressed " + x1 + " . " + y1);
//
//        canvas.getImage().updateAndDraw();
        }

    }

    public void mouseReleased(MouseEvent e) {
         if (IJ.getToolName().equals("rectangle")) {
        x2 = canvas.offScreenX(e.getX());
        y2 = canvas.offScreenY(e.getY());

//        System.out.println("released " + x2 + " , " + y2);
//        float w = x2 - x1;
//        float h = y2 - y1;
        //to do squares
        if (settings.getScale()) {
            float d = Math.max(x2 - x1, y2 - y1);

            y2 = y1 + d;
            x2 = x1 + d;
        }
        PolygonRoi pr = new PolygonRoi(new float[]{x1, x1, x2, x2}, new float[]{y1, y2, y2, y1}, 4, PolygonRoi.POLYGON);
        pr.setFillColor(new Color(1, 1, 0, 0.25f));

//        ov_rectangle.clear();
        ov_rectangle.add(pr);

        System.out.println("  ---> " + ov_rectangle.size());

        IJ.run(canvas.getImage(), "Select None", "");

//        x2 = 	canvas.offScreenX(e.getX());
//        y2 = 	canvas.offScreenY(e.getY());
//        IJ.log("released " + x2 + " . " + y2);
//        Roi rr = new Roi(x1, y1, (x2-x1), y2-y1);
//        rr.setFillColor(Color.YELLOW);
//        ov_rectangle.add(pr);
//        IJ.log("-> " + ov_rectangle.size());
        canvas.setOverlay(ov_rectangle);
        canvas.getImage().updateAndDraw();

        GenericDialog gd = new GenericDialog("CHOOSE...");
        gd.addChoice("choose ", namesGroups, namesGroups[0]);
        gd.showDialog();

        if (gd.wasCanceled()) {
            System.out.println("removing..");
            ov_rectangle.remove(ov_rectangle.size() - 1);
            canvas.setOverlay(ov_rectangle);
            canvas.getImage().updateAndDraw();
            return;
        }

        String aa = gd.getNextChoice();
        int n = -1;
        for (int i = 0; i < namesGroups.length; i++) {
            if (namesGroups[i].equals(aa)) {
                n = i;
                break;
            }
        }

        pr.setFillColor(ColorArray[n]);
        canvas.setOverlay(ov_rectangle);
        canvas.getImage().updateAndDraw();

        ImageProcessor ip = canvas.getImage().getChannelProcessor();
        ImageProcessor ipCopy = ip.duplicate();
        ipCopy.setRoi(pr);
        ipCopy = ipCopy.crop();
        ImagePlus impCopy = new ImagePlus(aa, ipCopy);

        if (settings.getScale()) {
            IJ.run(impCopy, "Scale...", "x=- y=- width=" + settings.getWScale() + " height=" + settings.getHScale() + " interpolation=Bicubic average create title=Scaling...");
            ImagePlus impScaled = WindowManager.getImage("Scaling...");
            impCopy = DuplicateImage(impScaled);
            impScaled.changes = false;
            impScaled.close();
        }

        String auxPath = settings.getPath() + File.separator + aa;
        File url = new File(auxPath);
        url.mkdirs();
        new FileSaver(impCopy).saveAsTiff(auxPath + File.separator + aa + "_" + i + ".tif");
        i++;
         }
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    public void mouseDragged(MouseEvent e) {
    }

    public void mouseMoved(MouseEvent e) {
    }

    public void run(String s) {

        //to open the dialog with the settings
        MainPanel panelMain = new MainPanel();
        GenericDialog gdG = new GenericDialog("Annotationer");
        gdG.add(panelMain);
        gdG.addMessage("");
        MainPanel panelD = (MainPanel) gdG.getComponent(0);
        gdG.showDialog();
        if (gdG.wasCanceled()) {
            return;
        }
        settings = new Setting(panelD.getTxtUrl(), panelD.getTxtNGroups(), panelD.getRbtnSquare(), panelD.getRbtnScaled(), panelD.getTxtWidth(), panelD.getTxtHeight());
        if ((settings.getPath().isEmpty()) || (settings.getNGroups() == 0)) {
            IJ.error("You have to write an url for the results and the number of groups that you need to work.");
            return;
        }
        namesGroups = new String[settings.getNGroups()];
        for (int i = 0; i < settings.getNGroups(); i++) {
            namesGroups[i] = "Group " + (i + 1);
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

        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);
        canvas.addKeyListener(this);
        wind.addKeyListener(this);
        ImagePlus.addImageListener(this);

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
