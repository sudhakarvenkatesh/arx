/*
 * ARX: Powerful Data Anonymization
 * Copyright 2012 - 2017 Fabian Prasser, Florian Kohlmayer and contributors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.deidentifier.arx.gui.view.impl.utility;

import java.util.ArrayList;
import java.util.List;

import org.deidentifier.arx.aggregates.StatisticsBuilderInterruptible;
import org.deidentifier.arx.aggregates.StatisticsQuality;
import org.deidentifier.arx.gui.Controller;
import org.deidentifier.arx.gui.model.ModelEvent.ModelPart;
import org.deidentifier.arx.gui.resources.Resources;
import org.deidentifier.arx.gui.view.SWTUtil;
import org.deidentifier.arx.gui.view.impl.common.ClipboardHandlerTable;
import org.deidentifier.arx.gui.view.impl.common.ComponentTitledSeparator;
import org.deidentifier.arx.gui.view.impl.common.async.Analysis;
import org.deidentifier.arx.gui.view.impl.common.async.AnalysisContext;
import org.deidentifier.arx.gui.view.impl.common.async.AnalysisManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import de.linearbits.swt.table.DynamicTable;
import de.linearbits.swt.table.DynamicTableColumn;

/**
 * This view displays results from different quality models
 *
 * @author Fabian Prasser
 */
public class ViewStatisticsQuality extends ViewStatistics<AnalysisContextQuality> {

    /** View */
    private Composite       root;

    /** View */
    private DynamicTable    table;

    /** View */
    private DynamicTable    table2;
    
    /** Internal stuff. */
    private AnalysisManager manager;

    /**
     * Creates a new instance.
     *
     * @param parent
     * @param controller
     * @param target
     * @param reset
     */
    public ViewStatisticsQuality(final Composite parent,
                                     final Controller controller,
                                     final ModelPart target,
                                     final ModelPart reset) {
        
        super(parent, controller, target, reset, true);
        this.manager = new AnalysisManager(parent.getDisplay());
    }
    
    @Override
    public LayoutUtility.ViewUtilityType getType() {
        return LayoutUtility.ViewUtilityType.QUALITY_MODELS;
    }

    /**
     * Creates a table item
     * @param attribute
     * @param quality
     */
    private void createItem(String attribute, StatisticsQuality quality) {
        TableItem item = new TableItem(this.table, SWT.NONE);
        item.setText(0, attribute);
        item.setText(1, quality.getDataType(attribute).getDescription().getLabel());
        setNumericValueAtIndex(item, 2, quality.getMissings().getValue(attribute));
        
        if (this.getTarget() == ModelPart.OUTPUT) {
            setNumericValueAtIndex(item, 3, quality.getGeneralizationIntensity().getValue(attribute));
            setNumericValueAtIndex(item, 4, quality.getGranularity().getValue(attribute));
            setNumericValueAtIndex(item, 5, quality.getNonUniformEntropy().getValue(attribute));
        }
    }

    /**
     * Returns a list of columns
     * @return
     */
    private List<String> getColumns() {
        
        // Prepare
        List<String> result = new ArrayList<>();
        
        // Input and output
        result.add(Resources.getMessage("ViewStatisticsQuality.0")); //$NON-NLS-1$
        result.add(Resources.getMessage("ViewStatisticsQuality.1")); //$NON-NLS-1$
        result.add(Resources.getMessage("ViewStatisticsQuality.2")); //$NON-NLS-1$
        
        // Output only
        if (getTarget() == ModelPart.OUTPUT) {
            result.add(Resources.getMessage("ViewStatisticsQuality.3")); //$NON-NLS-1$
            result.add(Resources.getMessage("ViewStatisticsQuality.4")); //$NON-NLS-1$
            result.add(Resources.getMessage("ViewStatisticsQuality.5")); //$NON-NLS-1$
        }
        
        // Return
        return result;
    }

    /**
     * Second set of columns
     * @return
     */
    private List<String> getColumns2() {
        List<String> result = new ArrayList<>();
        result.add(Resources.getMessage("ViewStatisticsQuality.6")); //$NON-NLS-1$
        result.add(Resources.getMessage("ViewStatisticsQuality.7")); //$NON-NLS-1$
        return result;
    }

    /**
     * Returns a formatted string for the value
     * @param item
     * @param index
     * @param value
     * @return
     */
    private void setNumericValueAtIndex(TableItem item, int index, double value) {
        if (Double.isNaN(value)) {
            item.setText(index, Resources.getMessage("ViewStatisticsQuality.8")); //$NON-NLS-1$
        } else {
            item.setData(String.valueOf(index), value);
        }
    }

    @Override
    protected Control createControl(Composite parent) {

        // Create root
        this.root = new Composite(parent, SWT.NONE);
        this.root.setLayout(SWTUtil.createGridLayout(1));
        
        // Prepare
        GridData separatordata = SWTUtil.createFillHorizontallyGridData(true, 3);
        separatordata.verticalIndent = 0;

        // Attribute-related
        ComponentTitledSeparator separator = new ComponentTitledSeparator(root, SWT.NONE);
        separator.setLayoutData(separatordata);
        separator.setText(Resources.getMessage("ViewStatisticsQuality.9")); //$NON-NLS-1$
        
        // Create table
        this.table = SWTUtil.createTableDynamic(root, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.FULL_SELECTION);
        this.table.setHeaderVisible(true);
        this.table.setLinesVisible(true);
        this.table.setMenu(new ClipboardHandlerTable(table).getMenu());
        this.table.setLayoutData(SWTUtil.createFillGridData());
        
        // Create columns
        List<String> columns = getColumns();
        int perc = (int)Math.floor(100d / (double)columns.size());
        for (String column : columns) {
            DynamicTableColumn c = new DynamicTableColumn(this.table, SWT.LEFT);
            c.setWidth(perc + "%", "30px"); //$NON-NLS-1$ //$NON-NLS-2$
            c.setText(column); //$NON-NLS-1$
            SWTUtil.createColumnWithBarCharts(table, c);
        }

        // Pack
        for (final TableColumn col : table.getColumns()) {
            col.pack();
        }
        SWTUtil.createGenericTooltip(table);
        
        if (getTarget() == ModelPart.OUTPUT) {

            // Attribute-related
            separator = new ComponentTitledSeparator(root, SWT.NONE);
            separator.setLayoutData(separatordata);
            separator.setText(Resources.getMessage("ViewStatisticsQuality.10")); //$NON-NLS-1$
            
            // Create table
            this.table2 = SWTUtil.createTableDynamic(root, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.FULL_SELECTION);
            this.table2.setHeaderVisible(true);
            this.table2.setLinesVisible(true);
            this.table2.setMenu(new ClipboardHandlerTable(table).getMenu());
            this.table2.setLayoutData(SWTUtil.createFillGridData());
            
            // Create columns
            List<String> columns2 = getColumns2();
            int perc2 = (int)Math.floor(100d / (double)columns2.size());
            for (String column : columns2) {
                DynamicTableColumn c = new DynamicTableColumn(this.table2, SWT.LEFT);
                c.setWidth(perc2 + "%", "30px"); //$NON-NLS-1$ //$NON-NLS-2$
                c.setText(column); //$NON-NLS-1$
                SWTUtil.createColumnWithBarCharts(table2, c);
            }

            // Pack
            for (final TableColumn col : table2.getColumns()) {
                col.pack();
            }
            SWTUtil.createGenericTooltip(table);
        }
        
        // Layout
        root.layout();
        
        // Return
        return root;
    }

    @Override
    protected AnalysisContextQuality createViewConfig(AnalysisContext context) {
        return new AnalysisContextQuality(context);
    }

    @Override
    protected void doReset() {
        if (this.manager != null) {
            this.manager.stop();
        }
        table.setRedraw(false);
        for (final TableItem i : table.getItems()) {
            i.dispose();
        }
        table.setRedraw(true);
        if (table2 != null) {
            table2.setRedraw(false);
            for (final TableItem i : table2.getItems()) {
                i.dispose();
            }
            table2.setRedraw(true);
        }
        setStatusEmpty();
    }

    @Override
    protected void doUpdate(AnalysisContextQuality context) {

        // The statistics builder
        final StatisticsBuilderInterruptible builder = context.handle.getStatistics().getInterruptibleInstance();
        
        // Create an analysis
        Analysis analysis = new Analysis(){
            
            private boolean           stopped = false;
            private StatisticsQuality quality;

            @Override
            public int getProgress() {
                return 0;
            }
            
            @Override
            public void onError() {
                setStatusEmpty();
            }

            @Override
            public void onFinish() {

                // Check
                if (stopped || !isEnabled()) {
                    return;
                }

                // Prepare
                table.setRedraw(false);
                
                // Clear
                for (final TableItem i : table.getItems()) {
                    i.dispose();
                }
                
                // Add
                for (String attribute : quality.getAttributes()) {
                    createItem(attribute, quality);
                }
                
                // Done
                table.setRedraw(true);
                table.layout();
                
                if (table2 != null) {

                    // Prepare
                    table2.setRedraw(false);
                    
                    // Clear
                    for (final TableItem i : table2.getItems()) {
                        i.dispose();
                    }
                    
                    TableItem item = new TableItem(table2, SWT.NONE);
                    item.setText(0, Resources.getMessage("ViewStatisticsQuality.11")); //$NON-NLS-1$
                    setNumericValueAtIndex(item, 1, quality.getGeneralizationIntensity().getArithmeticMean(false));
                    
                    item = new TableItem(table2, SWT.NONE);
                    item.setText(0, Resources.getMessage("ViewStatisticsQuality.12")); //$NON-NLS-1$
                    setNumericValueAtIndex(item, 1, quality.getGranularity().getArithmeticMean(false));
                    
                    item = new TableItem(table2, SWT.NONE);
                    item.setText(0, Resources.getMessage("ViewStatisticsQuality.13")); //$NON-NLS-1$
                    setNumericValueAtIndex(item, 1, quality.getNonUniformEntropy().getArithmeticMean(false));
                    
                    item = new TableItem(table2, SWT.NONE);
                    item.setText(0, Resources.getMessage("ViewStatisticsQuality.14")); //$NON-NLS-1$
                    setNumericValueAtIndex(item, 1, quality.getDiscernibility().getValue());
                    
                    item = new TableItem(table2, SWT.NONE);
                    item.setText(0, Resources.getMessage("ViewStatisticsQuality.15")); //$NON-NLS-1$
                    setNumericValueAtIndex(item, 1, quality.getAverageClassSize().getValue());
                    
                    item = new TableItem(table2, SWT.NONE);
                    item.setText(0, Resources.getMessage("ViewStatisticsQuality.16")); //$NON-NLS-1$
                    setNumericValueAtIndex(item, 1, quality.getKullbackLeiblerDivergence().getValue());
                    
                    item = new TableItem(table2, SWT.NONE);
                    item.setText(0, Resources.getMessage("ViewStatisticsQuality.17")); //$NON-NLS-1$
                    setNumericValueAtIndex(item, 1, quality.getSumOfSquaredErrors().getValue());
                    
                    // Done
                    table2.setRedraw(true);
                    table2.layout();
                }
                
                setStatusDone();
            }

            @Override
            public void onInterrupt() {
                if (!isEnabled()) {
                    setStatusEmpty();
                } else {
                    setStatusWorking();
                }
            }

            @Override
            public void run() throws InterruptedException {
                
                // Timestamp
                long time = System.currentTimeMillis();
                
                // Perform work
                this.quality = builder.getQualityStatistics();

                // Our users are patient
                while (System.currentTimeMillis() - time < MINIMAL_WORKING_TIME && !stopped){
                    Thread.sleep(10);
                }
            }

            @Override
            public void stop() {
                builder.interrupt();
                this.stopped = true;
            }
        };
        
        this.manager.start(analysis);
    }
    
    /**
     * Is an analysis running
     */
    protected boolean isRunning() {
        return manager != null && manager.isRunning();
    }
}