/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package patch;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 *
 * @author Gadea
 */
public class MainPanel extends javax.swing.JPanel {

    /**
     * Creates new form MainPanel
     */
    public MainPanel() {
        initComponents();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">                          
    private void initComponents() {

        lblUrlPos = new javax.swing.JLabel();
        txtUrlPos = new javax.swing.JTextField();
        lblUrlNeg = new javax.swing.JLabel();
        txtUrlNeg = new javax.swing.JTextField();
        btnUrlNeg = new javax.swing.JButton();
        btnUrlPos = new javax.swing.JButton();
        lblSizeRect = new javax.swing.JLabel();
        lblWidth = new javax.swing.JLabel();
        lblHeight = new javax.swing.JLabel();
        txtWidth = new javax.swing.JTextField();
        txtHeight = new javax.swing.JTextField();

        lblUrlPos.setText("Url to Positive cases");

        lblUrlNeg.setText("Url to Negative cases");

        btnUrlNeg.setText("Browse");
        btnUrlNeg.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnUrlNegActionPerformed(evt);
            }
        });

        btnUrlPos.setText("Browse");
        btnUrlPos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnUrlPosActionPerformed(evt);
            }
        });

        lblSizeRect.setText("Size of Rectangle (pixels):");

        lblWidth.setText("Width:");

        lblHeight.setText("Height:");

        txtWidth.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        txtWidth.setText("256");

        txtHeight.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        txtHeight.setText("256");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(lblUrlNeg)
                            .addComponent(lblUrlPos))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(txtUrlPos)
                            .addComponent(txtUrlNeg, javax.swing.GroupLayout.PREFERRED_SIZE, 225, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(btnUrlNeg)
                            .addComponent(btnUrlPos)))
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                        .addGroup(layout.createSequentialGroup()
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                .addComponent(lblWidth)
                                .addComponent(lblHeight))
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                .addComponent(txtWidth)
                                .addComponent(txtHeight, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addComponent(lblSizeRect)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblUrlPos)
                    .addComponent(txtUrlPos, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnUrlPos))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblUrlNeg)
                    .addComponent(txtUrlNeg, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnUrlNeg))
                .addGap(26, 26, 26)
                .addComponent(lblSizeRect)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblWidth)
                    .addComponent(txtWidth, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblHeight)
                    .addComponent(txtHeight, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>                        

    private void btnUrlPosActionPerformed(java.awt.event.ActionEvent evt) {                                          
        int result;
        JFileChooser chooser = new JFileChooser();

        chooser = new JFileChooser();
        chooser.setCurrentDirectory(new java.io.File("."));
        chooser.setDialogTitle("Browse");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); //only open directories
        //
        // disable the "All files" option.
        //
        chooser.setAcceptAllFileFilterUsed(false);
        //
        result = chooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            String dir = (chooser.getSelectedFile()).getPath();
            txtUrlPos.setText(dir);
        }
    }                                         

    private void btnUrlNegActionPerformed(java.awt.event.ActionEvent evt) {                                          
        int result;

        JFileChooser chooser = new JFileChooser();
        chooser = new JFileChooser();
        chooser.setCurrentDirectory(new java.io.File("."));
        chooser.setDialogTitle("Browse");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // only open direcctories

        result = chooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            String dir = (chooser.getSelectedFile()).getPath();
            txtUrlNeg.setText(dir);
        }
    }                                         


    // Variables declaration - do not modify                     
    private javax.swing.JButton btnUrlNeg;
    private javax.swing.JButton btnUrlPos;
    private javax.swing.JLabel lblHeight;
    private javax.swing.JLabel lblSizeRect;
    private javax.swing.JLabel lblUrlNeg;
    private javax.swing.JLabel lblUrlPos;
    private javax.swing.JLabel lblWidth;
    private javax.swing.JTextField txtHeight;
    private javax.swing.JTextField txtUrlNeg;
    private javax.swing.JTextField txtUrlPos;
    private javax.swing.JTextField txtWidth;
    // End of variables declaration                   

//properties (get&set)
    public String getTxtUrlPos() {
        return this.txtUrlPos.getText();
    }

    public String getTxtUrlNeg() {
        return this.txtUrlNeg.getText();
    }

    public int getTxtWidth() {
        return Integer.parseInt(this.txtWidth.getText());
    }

    public int getTxtHeight() {
        return Integer.parseInt(this.txtHeight.getText());
    }

}