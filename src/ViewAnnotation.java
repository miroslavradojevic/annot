import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.plugin.PlugIn;

import java.io.File;

/**
 * Created by miroslav on 11-7-14.
 */
public class ViewAnnotation implements PlugIn {


    public void run(String s) {

        IJ.open();
        ImagePlus annotated_img = IJ.getImage();

        String annotation_path = annotated_img.getOriginalFileInfo().directory;

        Overlay ov_annot;

        GenericDialog gd = new GenericDialog("Select Annotation");
        gd.addStringField("path to SWC ", annotation_path, 100);
        gd.showDialog();
        if (gd.wasCanceled()) return;
        annotation_path = gd.getNextString();

        ov_annot = new Overlay();

        int nr_bifs 	= 0;
        int nr_cross 	= 0;
        int nr_ends 	= 0;
        int nr_nons 	= 0;
        int nr_ignores = 0;

        if ((new File(annotation_path)).exists()) { // if it exists - update the overlays first

            ReadSWC reader = new ReadSWC(annotation_path);

            for (int i=0; i<reader.nodes.size(); i++) {

                int   type 	= Math.round(reader.nodes.get(i)[reader.TYPE]);
                float x 	= reader.nodes.get(i)[reader.XCOORD];
                float y 	= reader.nodes.get(i)[reader.YCOORD];
                float r 	= reader.nodes.get(i)[reader.RADIUS];

				/*
				file -> overlay
				 */
                OvalRoi c = new OvalRoi(x-r+.0f, y-r+.0f, 2*r, 2*r);

                if (type==Annotationer.none_type) {
                    c.setFillColor(Annotationer.none_color);
                    c.setStrokeColor(Annotationer.none_color);
                    nr_nons++;
                }
                else if (type==Annotationer.end_type) {
                    c.setFillColor(Annotationer.end_color);
                    c.setStrokeColor(Annotationer.end_color);
                    nr_ends++;
                }
                else if (type==Annotationer.bif_type) {
                    c.setFillColor(Annotationer.bif_color);
                    c.setStrokeColor(Annotationer.bif_color);
                    nr_bifs++;
                }
                else if (type==Annotationer.cross_type) {
                    c.setFillColor(Annotationer.cross_color);
                    c.setStrokeColor(Annotationer.cross_color);
                    nr_cross++;
                }
                else if (type==Annotationer.ignore_type) {
                    c.setFillColor(Annotationer.ignore_color);
                    c.setStrokeColor(Annotationer.ignore_color);
                    nr_ignores++;
                }

                ov_annot.add(c);
            }

        }

        annotated_img.setOverlay(ov_annot);

        IJ.log("loaded " + nr_bifs + " bifs, " + nr_ends + " ends, " + nr_nons + " nons, " + nr_cross + " crosses, " + nr_ignores + " ignores");
        IJ.setTool("hand");

    }
}
