/*
 * Copyright 2006-2020 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package io.github.mzmine.datamodel.features.types;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.types.graphicalnodes.FeatureShapeChart;
import io.github.mzmine.datamodel.features.types.modifiers.GraphicalColumType;
import io.github.mzmine.datamodel.features.types.tasks.FeaturesGraphicalNodeTask;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.visualization.chromatogram.ChromatogramVisualizerModule;
import io.github.mzmine.modules.visualization.chromatogram.TICPlotType;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.taskcontrol.TaskPriority;
import io.github.mzmine.util.FeatureUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.Node;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.layout.StackPane;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Contains no data - listens to changes in all {@link ModularFeature} in a {@link
 * ModularFeatureListRow} On change of any {@link FeatureDataType} - the chart is updated
 *
 * @author Robin Schmid (https://github.com/robinschmid)
 */
public class FeatureShapeType extends LinkedDataType
    implements GraphicalColumType<Boolean> {

  @Nonnull
  @Override
  public String getHeaderString() {
    return "Shapes";
  }

  @Override
  public Node getCellNode(TreeTableCell<ModularFeatureListRow, Boolean> cell,
      TreeTableColumn<ModularFeatureListRow, Boolean> coll,
      Boolean cellData, RawDataFile raw) {
    ModularFeatureListRow row = cell.getTreeTableRow().getItem();
    if (row == null) {
      return null;
    }

    // get existing buffered node from row (for column name)
    // TODO listen to changes in features data
    Node node = row.getBufferedColChart(coll.getText());
    if (node != null) {
      return node;
    }

    StackPane pane = new StackPane();

    // TODO stop task if new task is started
    Task task = new FeaturesGraphicalNodeTask(FeatureShapeChart.class, pane, row, coll.getText());
    // TODO change to TaskPriority.LOW priority
    MZmineCore.getTaskController().addTask(task, TaskPriority.HIGH);

    return pane;
  }

  @Override
  public double getColumnWidth() {
    return DEFAULT_GRAPHICAL_CELL_WIDTH;
  }

  @Nullable
  @Override
  public Runnable getDoubleClickAction(@Nonnull ModularFeatureListRow row,
      @Nonnull List<RawDataFile> rawDataFiles) {

    List<Feature> features = new ArrayList<>();
    rawDataFiles.forEach(rawDataFile -> features.add(row.getFeature(rawDataFile)));
    if (features.isEmpty()) {
      return null;
    }
    return () -> {
      ScanSelection selection = new ScanSelection(1);
      Map<Feature, String> labels = new HashMap<>();
      features.forEach(f -> labels.put(f, FeatureUtils.featureToString(f)));
      ChromatogramVisualizerModule
          .showNewTICVisualizerWindow(rawDataFiles.toArray(new RawDataFile[0]), features, labels,
              selection, TICPlotType.BASEPEAK, rawDataFiles.get(0).getDataMZRange(1));
    };
  }

}
