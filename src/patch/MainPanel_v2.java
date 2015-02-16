/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package annot.src.patch;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 *
 * @author Gadea
 */
public class MainPanel_v2 extends javax.swing.JPanel {

    /**
     * Creates new form MainPanel
     */
    public MainPanel_v2() {
        initComponents();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        lblUrl = new javax.swing.JLabel();
        txtUrl = new javax.swing.JTextField();
        btnUrl = new javax.swing.JButton();
        lblSizeRectRand = new javax.swing.JLabel();
        lblwxhRand = new javax.swing.JLabel();
        lblxRand = new javax.swing.JLabel();
        txtWidthRand = new javax.swing.JTextField();
        txtHeightRand = new javax.swing.JTextField();
        lblNumPatches = new javax.swing.JLabel();
        txtNumPatches = new javax.swing.JTextField();
        jSeparator1 = new javax.swing.JSeparator();
        lblRand = new javax.swing.JLabel();

        lblUrl.setText("Url Results:");

        btnUrl.setText("Browse");
        btnUrl.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnUrlActionPerformed(evt);
            }
        });

        lblSizeRectRand.setText("Size of Rectangle (pixels):");

        lblwxhRand.setText("width x height ");

        lblxRand.setText("x");

        txtWidthRand.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        txtWidthRand.setText("500");

        txtHeightRand.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        txtHeightRand.setText("500");

        lblNumPatches.setText("Number of patches");

        txtNumPatches.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        txtNumPatches.setText("100");

        lblRand.setText("Crop random patches:");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(jSeparator1)
                        .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                            .addGap(6, 6, 6)
                            .addComponent(lblUrl)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                            .addComponent(txtUrl, javax.swing.GroupLayout.PREFERRED_SIZE, 225, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                            .addComponent(btnUrl))
                        .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                            .addGap(31, 31, 31)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                    .addComponent(lblNumPatches)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                    .addComponent(txtNumPatches, javax.swing.GroupLayout.PREFERRED_SIZE, 33, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGroup(layout.createSequentialGroup()
                                    .addComponent(lblSizeRectRand)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(txtWidthRand, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(lblxRand, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                    .addComponent(txtHeightRand, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                    .addComponent(lblwxhRand)))))
                    .addComponent(lblRand))
                .addContainerGap(18, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblUrl)
                    .addComponent(txtUrl, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnUrl))
                .addGap(18, 18, 18)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lblRand)
                .addGap(7, 7, 7)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblSizeRectRand)
                    .addComponent(txtWidthRand, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtHeightRand, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblwxhRand)
                    .addComponent(lblxRand))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblNumPatches)
                    .addComponent(txtNumPatches, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void btnUrlActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnUrlActionPerformed
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
            txtUrl.setText(dir);
        }
    }//GEN-LAST:event_btnUrlActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnUrl;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JLabel lblNumPatches;
    private javax.swing.JLabel lblRand;
    private javax.swing.JLabel lblSizeRectRand;
    private javax.swing.JLabel lblUrl;
    private javax.swing.JLabel lblwxhRand;
    private javax.swing.JLabel lblxRand;
    private javax.swing.JTextField txtHeightRand;
    private javax.swing.JTextField txtNumPatches;
    private javax.swing.JTextField txtUrl;
    private javax.swing.JTextField txtWidthRand;
    // End of variables declaration//GEN-END:variables

//properties (get&set)
    public String getTxtUrl() {
        return this.txtUrl.getText();
    }

    public int getNumPatches() {
        return Integer.parseInt(this.txtNumPatches.getText());
    }
    public int getTxtWidthRand()
    {
        return Integer.parseInt(this.txtWidthRand.getText());
    }
    public int getTxtHeightRand()
    {
        return Integer.parseInt(this.txtHeightRand.getText());
    }
}
