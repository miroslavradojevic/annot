/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package patch;

/**
 *
 * @author Gadea
 */
public class Setting {
    
    private String path;
    private int nGroups;
    private boolean square;
    private boolean scale;
    private int wScale;
    private int hScale;
    
   
    
    public Setting(String path, int nGroups, boolean square, boolean scale, int wScale, int hScale)
    {
        this.path=path;
        this.nGroups=nGroups;
        this.square=square;
        this.scale=scale;
        this.wScale=wScale;
        this.hScale=hScale;
    }
    public String getPath(){
        return path;
    }
    public int getNGroups()
    {
        return nGroups;
    }
    public boolean getSquare(){
        return square;
    }
    public boolean getScale()
    {
        return scale;
    }
    public int getWScale()
    {
        return wScale;
    }
    public int getHScale()
    {
        return hScale;
    }
    
}
