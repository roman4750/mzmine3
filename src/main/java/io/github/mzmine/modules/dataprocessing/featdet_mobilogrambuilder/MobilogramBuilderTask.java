package io.github.mzmine.modules.dataprocessing.featdet_mobilogrambuilder;

import io.github.mzmine.datamodel.Frame;
import io.github.mzmine.datamodel.MobilityType;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.project.impl.StorableFrame;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MobilogramUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Worker task of the mobilogram builder
 */
public class MobilogramBuilderTask extends AbstractTask {

  private static Logger logger = Logger.getLogger(MobilogramBuilderTask.class.getName());

  private final Set<Frame> frames;
  private final MZTolerance mzTolerance;
  private final String massList;
  private final int totalFrames;
  private final int minPeaks;
  private final boolean addDpFromRaw;
  private final ScanSelection scanSelection;
  private int processedFrames;

  public MobilogramBuilderTask(List<Frame> frames, ParameterSet parameters) {
    this.mzTolerance = parameters.getParameter(MobilogramBuilderParameters.mzTolerance).getValue();
    this.massList = parameters.getParameter(MobilogramBuilderParameters.massList).getValue();
    this.minPeaks = parameters.getParameter(MobilogramBuilderParameters.minPeaks).getValue();
    this.addDpFromRaw = parameters.getParameter(MobilogramBuilderParameters.addRawDp).getValue();
    this.scanSelection =
        parameters.getParameter(MobilogramBuilderParameters.scanSelection).getValue();
//    this.frames = frames;
    this.frames = (Set<Frame>) scanSelection.getMachtingScans((frames));

    totalFrames = (this.frames.size() != 0) ? this.frames.size() : 1;
    setStatus(TaskStatus.WAITING);
  }

  @Override
  public String getTaskDescription() {
    return "Detecting mobilograms for frames. " + processedFrames + "/" + totalFrames;
  }

  @Override
  public double getFinishedPercentage() {
    return (double) processedFrames / totalFrames;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);

    for (Frame frame : frames) {

      if (isCanceled()) {
        return;
      }

      if (!(frame instanceof StorableFrame) || !scanSelection.matches(frame)) {
        continue;
      }

      List<Scan> eligibleScans = frame.getMobilityScans().stream().filter(
          scanSelection::matches).collect(Collectors.toList());
      List<Mobilogram> mobilograms =
          calculateMobilogramsForScans(eligibleScans);

      if (addDpFromRaw) {
        addDataPointsFromRaw(mobilograms, frame.getMobilityScans());
      }
      printDuplicateStatistics(mobilograms);
      mobilograms
          .forEach(mob -> MobilogramUtils.fillMissingScanNumsWithZero((SimpleMobilogram) mob));
//      mobilograms.forEach(mob -> ((SimpleMobilogram)mob).fillEdgesWithZeros(3));

      frame.clearMobilograms();
      mobilograms.forEach(frame::addMobilogram);
      processedFrames++;
    }

    setStatus(TaskStatus.FINISHED);
  }

  protected List<Mobilogram> calculateMobilogramsForScans(List<Scan> scans) {
    if (scans.size() == 0 || scans.get(0).getMassList(massList) == null) {
      return Collections.emptyList();
    }

    final MobilityType mobilityType = scans.get(0).getMobilityType();
    int numDp = 0;

    for (Scan scan : scans) {
      numDp += scan.getMassList(massList).getDataPoints().length;
    }
    final List<MobilityDataPoint> allDps = new ArrayList<>(numDp);

    for (Scan scan : scans) {
      Arrays.stream(scan.getMassList(massList).getDataPoints()).forEach(
          dp -> allDps.add(new MobilityDataPoint(dp.getMZ(), dp.getIntensity(), scan.getMobility(),
              scan.getScanNumber())));
    }

    // sort by highest dp, we assume that that measurement was the most accurate
    allDps.sort(Comparator.comparingDouble(MobilityDataPoint::getIntensity));

    List<Mobilogram> mobilograms = new ArrayList<>();
    List<MobilityDataPoint> itemsToRemove = new ArrayList<>();

    for (int i = 0; i < allDps.size(); i++) {
      if (isCanceled()) {
        return null;
      }

      final MobilityDataPoint baseDp = allDps.get(i);
      final double baseMz = baseDp.getMZ();
      allDps.remove(baseDp);
      i--; // item removed

      final SimpleMobilogram mobilogram = new SimpleMobilogram(mobilityType, null);
      mobilogram.addDataPoint(baseDp);

      // go through all dps and add mzs within tolerance
      for (MobilityDataPoint dp : allDps) {
        if (mzTolerance.checkWithinTolerance(baseMz, dp.getMZ())
            && !mobilogram.containsDpForScan(dp.getScanNum())) {
          mobilogram.addDataPoint(dp);
          itemsToRemove.add(dp);
        }
      }

      allDps.removeAll(itemsToRemove);
      itemsToRemove.clear();

      if (mobilogram.getDataPoints().size() > minPeaks) {
        mobilogram.calc();
        mobilograms.add(mobilogram);
      }
    }

    mobilograms.sort(Comparator.comparingDouble(Mobilogram::getMZ));
    return mobilograms;
  }

  private void addDataPointsFromRaw(List<Mobilogram> mobilograms, Collection<Scan> rawScans) {
    // rawScans are actually StorableScans so data points are stored on the hard disc. We preload
    // everything here at once
    int numDp = 0;
    for (Scan scan : rawScans) {
      numDp += scan.getDataPoints().length;
    }
    final List<MobilityDataPoint> allDps = new ArrayList<>(numDp);
    for (Scan scan : rawScans) {
      Arrays.stream(scan.getDataPoints()).forEach(
          dp -> allDps.add(new MobilityDataPoint(dp.getMZ(), dp.getIntensity(), scan.getMobility(),
              scan.getScanNumber())));
    }
    // if we sort here, we can use break conditions later
    allDps.sort(Comparator.comparingDouble(MobilityDataPoint::getMZ));

    for (Mobilogram mobilogram : mobilograms) {
      if (isCanceled()) {
        return;
      }

      Date start = new Date();
      // todo maybe mobilogram.getMZRange()?
      double lowerMzLimit = mzTolerance.getToleranceRange(mobilogram.getMZ()).lowerEndpoint();
      double upperMzLimit = mzTolerance.getToleranceRange(mobilogram.getMZ()).upperEndpoint();

      int lowerStartIndex = -1;
      int upperStopIndex = allDps.size() - 1;
      for (int i = 0; i < allDps.size(); i++) {
        if (allDps.get(i).getMZ() >= lowerMzLimit) {
          lowerStartIndex = i;
          break;
        }
      }
      if (lowerStartIndex == -1) {
        continue;
      }
      for (int i = lowerStartIndex; i < allDps.size(); i++) {
        if (allDps.get(i).getMZ() >= upperMzLimit) {
          upperStopIndex = i;
          break;
        }
      }

      // all dps within mztolerance and not already in the mobilogram
      Date preSearch = new Date();
      List<MobilityDataPoint> eligibleDps =
          allDps.subList(lowerStartIndex, upperStopIndex + 1).stream()
              .filter(dp -> !mobilogram.getScanNumbers().contains(dp.getScanNum())).collect(
              Collectors.toList());
      Date done = new Date();

      final long full = done.getTime() - start.getTime();
      final long search = done.getTime() - preSearch.getTime();

//      logger.info(
//          () -> "adding " + eligibleDps.size() + " dp to " + mobilogram.representativeString()
//              + " - full: " + full + " ms - search " + search + " ms");

      for (MobilityDataPoint dp : eligibleDps) {
        ((SimpleMobilogram) mobilogram).addDataPoint(dp);
      }
      ((SimpleMobilogram) mobilogram).calc();
    }
    // todo compare if other entries from that scan would have been better
  }

  private void printDuplicateStatistics(List<Mobilogram> mobilograms) {

    List<Mobilogram> copyMobilograms = new ArrayList<>(mobilograms);

    int overlapCounter = 0;
    for (Mobilogram baseMob : mobilograms) {
      copyMobilograms.remove(baseMob);
      for (Mobilogram copyMob : copyMobilograms) {
        if (baseMob.getMZRange().isConnected(copyMob.getMobilityRange())) {
          overlapCounter++;
        }
      }
    }
    logger.info("Found " + overlapCounter + " overlaps within " + mobilograms.size());
  }
}
