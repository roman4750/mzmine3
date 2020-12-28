package io.github.mzmine.datamodel;

import java.util.List;
import io.github.mzmine.modules.io.rawdataimport.fileformats.imzmlimport.ImagingParameters;


/**
 * mark data as imaging raw data
 * 
 * @author r_schm33
 *
 */
public interface ImagingRawDataFile extends RawDataFile {

  public void setImagingParam(ImagingParameters imagingParameters);

  public ImagingParameters getImagingParam();

  public Scan getScan(double x, double y);

  /**
   * all scans in this area
   *
   * @param x
   * @param y
   * @param x2 inclusive
   * @param y2 inclusive
   * @return
   */
  public List<Scan> getScansInArea(float x, float y, float x2, float y2);

}