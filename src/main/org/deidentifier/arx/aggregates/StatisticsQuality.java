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
package org.deidentifier.arx.aggregates;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.deidentifier.arx.ARXConfiguration;
import org.deidentifier.arx.DataHandle;
import org.deidentifier.arx.DataType;
import org.deidentifier.arx.aggregates.quality.QualityConfiguration;
import org.deidentifier.arx.aggregates.quality.QualityDomainShare;
import org.deidentifier.arx.aggregates.quality.QualityDomainShareRaw;
import org.deidentifier.arx.aggregates.quality.QualityDomainShareRedaction;
import org.deidentifier.arx.aggregates.quality.QualityMeasureColumnOriented;
import org.deidentifier.arx.aggregates.quality.QualityMeasureRowOriented;
import org.deidentifier.arx.aggregates.quality.QualityModelColumnOrientedLoss;
import org.deidentifier.arx.aggregates.quality.QualityModelColumnOrientedNonUniformEntropy;
import org.deidentifier.arx.aggregates.quality.QualityModelColumnOrientedPrecision;
import org.deidentifier.arx.aggregates.quality.QualityModelRowOrientedAECS;
import org.deidentifier.arx.aggregates.quality.QualityModelRowOrientedAmbiguity;
import org.deidentifier.arx.aggregates.quality.QualityModelRowOrientedDiscernibility;
import org.deidentifier.arx.aggregates.quality.QualityModelRowOrientedKLDivergence;
import org.deidentifier.arx.aggregates.quality.QualityModelRowOrientedSSE;
import org.deidentifier.arx.common.Groupify;
import org.deidentifier.arx.common.TupleWrapper;
import org.deidentifier.arx.common.WrappedBoolean;
import org.deidentifier.arx.common.WrappedInteger;
import org.deidentifier.arx.exceptions.ComputationInterruptedException;

/**
 * Encapsulates statistics obtained using various quality models
 *
 * @author Fabian Prasser
 */
public class StatisticsQuality {

    /** Column-oriented model */
    private QualityMeasureColumnOriented       loss;
    /** Column-oriented model */
    private QualityMeasureColumnOriented       entropy;
    /** Column-oriented model */
    private QualityMeasureColumnOriented       precision;

    /** Row-oriented model */
    private QualityMeasureRowOriented          aecs;
    /** Row-oriented model */
    private QualityMeasureRowOriented          ambiguity;
    /** Row-oriented model */
    private QualityMeasureRowOriented          discernibility;
    /** Row-oriented model */
    private QualityMeasureRowOriented          kldivergence;
    /** Row-oriented model */
    private QualityMeasureRowOriented          sse;

    /** Quality */
    private final List<String>                 attributes;
    /** Quality */
    private final Map<String, DataType<?>>     datatypes;
    /** Quality */
    private final QualityMeasureColumnOriented missings;

    /** State */
    private WrappedBoolean                     stop;
    /** State */
    private WrappedInteger                     progress;

    /**
     * Creates a new instance
     * @param input
     * @param output
     * @param config
     * @param stop
     * @param progress
     */
    StatisticsQuality(DataHandle input,
                      DataHandle output,
                      ARXConfiguration config,
                      WrappedBoolean stop,
                      WrappedInteger progress) {
     
        // State
        this.stop = stop;
        this.progress = progress;
        
        // Build config
        QualityConfiguration configuration = new QualityConfiguration();
        // TODO: Do something with ARXConfiguration here.
        
        // Extract quasi-identifiers
        int[] indices = getIndicesOfQuasiIdentifiers(input);
        
        // Basic measures
        this.attributes = getAttributes(output, indices);
        this.datatypes = getDataTypes(output, indices);
        this.missings = getMissings(output, indices);

        this.progress.value = 10;
        
        // Special case: we are checking the input dataset
        if (input == output) {
            
            // Column oriented
            this.loss = new QualityMeasureColumnOriented(input, indices);
            this.entropy = new QualityMeasureColumnOriented(input, indices);
            this.precision = new QualityMeasureColumnOriented(input, indices);

            // Row oriented
            this.aecs = new QualityMeasureRowOriented(0d, 0d, 1d);
            this.ambiguity = new QualityMeasureRowOriented(0d, 0d, 1d);
            this.discernibility = new QualityMeasureRowOriented(0d, 0d, 1d);
            this.kldivergence = new QualityMeasureRowOriented(0d, 0d, 1d);
            this.sse = new QualityMeasureRowOriented(0d, 0d, 1d);
            this.progress.value = 100;
            
            // Break
            return;
        }
        
        // Pre-computed frequently needed data
        Groupify<TupleWrapper> groupedInput = this.getGroupify(input, indices);
        Groupify<TupleWrapper> groupedOutput = this.getGroupify(output, indices);
        String[][][] hierarchies = getHierarchies(input, indices, configuration);
        QualityDomainShare[] shares = getDomainShares(input, indices, hierarchies, configuration);

        this.progress.value = 20;
        
        // Build
        try {
            
            this.loss = new QualityModelColumnOrientedLoss(stop,
                                                           input,
                                                           output,
                                                           groupedInput,
                                                           groupedOutput,
                                                           hierarchies,
                                                           shares,
                                                           indices,
                                                           configuration).evaluate();
            this.checkInterrupt();
        } catch (Exception e) {
            // Fail silently
            this.loss = new QualityMeasureColumnOriented();
        }
        this.progress.value = 30;

        // Build
        try {
            this.entropy = new QualityModelColumnOrientedNonUniformEntropy(stop,
                                                                           input,
                                                                           output,
                                                                           groupedInput,
                                                                           groupedOutput,
                                                                           hierarchies,
                                                                           shares,
                                                                           indices,
                                                                           configuration).evaluate();
            this.checkInterrupt();
        } catch (Exception e) {
            // Fail silently
            this.entropy = new QualityMeasureColumnOriented();
        }
        this.progress.value = 40;

        // Build
        try {
            this.precision = new QualityModelColumnOrientedPrecision(stop,
                                                                     input,
                                                                     output,
                                                                     groupedInput,
                                                                     groupedOutput,
                                                                     hierarchies,
                                                                     shares,
                                                                     indices,
                                                                     configuration).evaluate();
            this.checkInterrupt();
        } catch (Exception e) {
            // Fail silently
            this.precision = new QualityMeasureColumnOriented();
        }
        this.progress.value = 50;

        // Build
        try {
            this.aecs = new QualityModelRowOrientedAECS(stop,
                                                        input,
                                                        output,
                                                        groupedInput,
                                                        groupedOutput,
                                                        hierarchies,
                                                        shares,
                                                        indices,
                                                        configuration).evaluate();
            this.checkInterrupt();
        } catch (Exception e) {
            // Fail silently
            this.aecs = new QualityMeasureRowOriented();
        }
        this.progress.value = 60;

        // Build
        try {
            this.ambiguity = new QualityModelRowOrientedAmbiguity(stop,
                                                                  input,
                                                                  output,
                                                                  groupedInput,
                                                                  groupedOutput,
                                                                  hierarchies,
                                                                  shares,
                                                                  indices,
                                                                  configuration).evaluate();
            this.checkInterrupt();
        } catch (Exception e) {
            // Fail silently
            this.ambiguity = new QualityMeasureRowOriented();
        }
        this.progress.value = 70;

        // Build
        try {
            this.discernibility = new QualityModelRowOrientedDiscernibility(stop,
                                                                            input,
                                                                            output,
                                                                            groupedInput,
                                                                            groupedOutput,
                                                                            hierarchies,
                                                                            shares,
                                                                            indices,
                                                                            configuration).evaluate();
            this.checkInterrupt();
        } catch (Exception e) {
            // Fail silently
            this.discernibility = new QualityMeasureRowOriented();
        }
        this.progress.value = 80;

        // Build
        try {
            this.kldivergence = new QualityModelRowOrientedKLDivergence(stop,
                                                                        input,
                                                                        output,
                                                                        groupedInput,
                                                                        groupedOutput,
                                                                        hierarchies,
                                                                        shares,
                                                                        indices,
                                                                        configuration).evaluate();
            this.checkInterrupt();
        } catch (Exception e) {
            // Fail silently
            this.kldivergence = new QualityMeasureRowOriented();
        }
        this.progress.value = 90;

        // Build
        try {
            this.sse = new QualityModelRowOrientedSSE(stop,
                                                      input,
                                                      output,
                                                      groupedInput,
                                                      groupedOutput,
                                                      hierarchies,
                                                      shares,
                                                      indices,
                                                      configuration).evaluate();
            this.checkInterrupt();
        } catch (Exception e) {
            // Fail silently
            this.sse = new QualityMeasureRowOriented();
        }
        this.progress.value = 100;
    }

    /**
     * Quality according to the "Ambiguity" model proposed in:<br>
     * <br>
     * Goldberger, Tassa: "Efficient Anonymizations with Enhanced Utility"
     * Trans Data Priv
     * 
     * @return Quality measure
     */
    public QualityMeasureRowOriented getAmbiguity() {
        return ambiguity;
    }

    /**
     * Returns a list of all attributes considered
     * 
     * @return the attributes
     */
    public List<String> getAttributes() {
        return attributes;
    }

    /**
     * Quality according to the "AECS" model proposed in:<br>
     * <br>
     * K. LeFevre, D. DeWitt, R. Ramakrishnan: "Mondrian multidimensional k-anonymity"
     * Proc Int Conf Data Engineering, 2006.
     * 
     * @return Quality measure
     */
    public QualityMeasureRowOriented getAverageClassSize() {
        return aecs;
    }

    /**
     * Returns the data type for the attribute
     * @param attribute
     * @return
     */
    public DataType<?> getDataType(String attribute) {
        return datatypes.get(attribute);
    }

    /**
     * Quality according to the "Discernibility" model proposed in:<br>
     * <br>
     * R. Bayardo, R. Agrawal: "Data privacy through optimal k-anonymization"
     * Proc Int Conf Data Engineering, 2005, pp. 217-228
     * 
     * @return Quality measure
     */
    public QualityMeasureRowOriented getDiscernibility() {
        return discernibility;
    }

    /**
     * Quality according to the "Precision" model proposed in:<br>
     * <br>
     * L. Sweeney: "Achieving k-anonymity privacy protection using generalization and suppression"
     * J Uncertain Fuzz Knowl Sys 10 (5) (2002) 571-588.
     * 
     * @return Quality measure
     */
    public QualityMeasureColumnOriented getGeneralizationIntensity() {
        return precision;
    }

    /**
     * Quality according to the "Loss" model proposed in:<br>
     * <br>
     * Iyengar, V.: "Transforming data to satisfy privacy constraints"
     * Proc Int Conf Knowl Disc Data Mining, p. 279-288 (2002)
     * 
     * @return Quality measure
     */
    public QualityMeasureColumnOriented getGranularity() {
        return loss;
    }

    /**
     * Quality according to the "KL-Divergence" model proposed in:<br>
     * <br>
     * Ashwin Machanavajjhala, Daniel Kifer, Johannes Gehrke, Muthuramakrishnan Venkitasubramaniam: <br>
     * L-diversity: Privacy beyond k-anonymity<br>
     * ACM Transactions on Knowledge Discovery from Data (TKDD), Volume 1 Issue 1, March 2007
     * 
     * @return Quality measure
     */
    public QualityMeasureRowOriented getKullbackLeiblerDivergence() {
        return kldivergence;
    }

    /**
     * Returns the fraction of missing values of the attributes considered
     * 
     * @return the datatypes
     */
    public QualityMeasureColumnOriented getMissings() {
        return missings;
    }

    /**
     * Quality according to the "Non-Uniform Entropy" model proposed in:<br>
     * <br>
     * A. De Waal and L. Willenborg: "Information loss through global recoding and local suppression"
     * Netherlands Off Stat, vol. 14, pp. 17-20, 1999.
     * 
     * @return Quality measure
     */
    public QualityMeasureColumnOriented getNonUniformEntropy() {
        return entropy;
    }

    /**
     * Quality according to the "SSE" model proposed in:<br>
     * <br>
     * Soria-Comas, Jordi, et al.:
     * "t-closeness through microaggregation: Strict privacy with enhanced utility preservation."
     * IEEE Transactions on Knowledge and Data Engineering 27.11 (2015):
     * 3098-3110.
     * 
     * @return Quality measure
     */
    public QualityMeasureRowOriented getSumOfSquaredErrors() {
        return sse;
    }

    /**
     * Checks whether an interruption happened.
     */
    private void checkInterrupt() {
        if (stop.value) {
            throw new ComputationInterruptedException("Interrupted");
        }
    }


    /**
     * Returns a list of the attributes covered
     * @param output
     * @param indices
     * @return
     */
    private List<String> getAttributes(DataHandle output, int[] indices) {
        List<String> result = new ArrayList<>();
        for (int index : indices) {
            result.add(output.getAttributeName(index));
        }
        return result;
    }

    /**
     * Returns all data types
     * @param output
     * @param indices
     * @return
     */
    private Map<String, DataType<?>> getDataTypes(DataHandle output, int[] indices) {
        Map<String, DataType<?>> result = new HashMap<>();
        for (String attribute : getAttributes(output, indices)) {
            result.put(attribute, output.getDataType(attribute));
        }
        return result;
    }

    /**
     * Returns domain shares for the handle
     * @param handle
     * @param indices
     * @param hierarchies
     * @param config
     * @return
     */
    private QualityDomainShare[] getDomainShares(DataHandle handle, 
                                                 int[] indices,
                                                 String[][][] hierarchies,
                                                 QualityConfiguration config) {

        // Prepare
        QualityDomainShare[] shares = new QualityDomainShare[indices.length];
        
        // Compute domain shares
        for (int i=0; i<shares.length; i++) {
            
            try {
                
                // Extract info
                String[][] hierarchy = hierarchies[i];
                String attribute = handle.getAttributeName(indices[i]);
                HierarchyBuilder<?> builder = handle.getDefinition().getHierarchyBuilder(attribute);
                
                // Create shares for redaction-based hierarchies
                if (builder != null && (builder instanceof HierarchyBuilderRedactionBased) &&
                    ((HierarchyBuilderRedactionBased<?>)builder).isDomainPropertiesAvailable()){
                    shares[i] = new QualityDomainShareRedaction((HierarchyBuilderRedactionBased<?>)builder);
                    
                // Create fallback-shares for materialized hierarchies
                // TODO: Interval-based hierarchies are currently not compatible
                } else {
                    shares[i] = new QualityDomainShareRaw(hierarchy, config.getSuppressedValue());
                }
                
            } catch (Exception e) {
                // Ignore silently
                shares[i] = null;
            }
        }

        // Return
        return shares;
    }

    /**
     * Returns a groupified version of the dataset
     * 
     * @param handle
     * @param indices
     * @return
     */
    private Groupify<TupleWrapper> getGroupify(DataHandle handle, int[] indices) {
        
        // Prepare
        int capacity = handle.getNumRows() / 10;
        capacity = capacity > 10 ? capacity : 10;
        Groupify<TupleWrapper> groupify = new Groupify<TupleWrapper>(capacity);
        int numRows = handle.getNumRows();
        for (int row = 0; row < numRows; row++) {
            TupleWrapper tuple = new TupleWrapper(handle, indices, row);
            groupify.add(tuple);
            checkInterrupt();
        }
        
        return groupify;
    }

    /**
     * Returns hierarchies, creates trivial hierarchies if no hierarchy is found.
     * Adds an additional level, if there is no root node
     * 
     * @param handle
     * @param indices
     * @param config
     * @return
     */
    private String[][][] getHierarchies(DataHandle handle, 
                                        int[] indices,
                                        QualityConfiguration config) {
        
        String[][][] hierarchies = new String[indices.length][][];
        
        // Collect hierarchies
        for (int i=0; i<indices.length; i++) {
            
            // Extract and store
            String attribute = handle.getAttributeName(indices[i]);
            String[][] hierarchy = handle.getDefinition().getHierarchy(attribute);
            
            // If not empty
            if (hierarchy != null && hierarchy.length != 0 && hierarchy[0] != null && hierarchy[0].length != 0) {
                
                // Clone
                hierarchies[i] = hierarchy.clone();
                
            } else {
                
                // Create trivial hierarchy
                String[] values = handle.getDistinctValues(indices[i]);
                hierarchies[i] = new String[values.length][2];
                for (int j = 0; j < hierarchies[i].length; j++) {
                    hierarchies[i][j][0] = values[j];
                    hierarchies[i][j][1] = config.getSuppressedValue();
                }
            }
        }

        // Fix hierarchy (if suppressed character is not contained in generalization hierarchy)
        for (int j=0; j<indices.length; j++) {
            
            // Access
            String[][] hierarchy = hierarchies[j];
            
            // Check if there is a problem
            Set<String> values = new HashSet<String>();
            for (int i = 0; i < hierarchy.length; i++) {
                String[] levels = hierarchy[i];
                values.add(levels[levels.length - 1]);
            }
            
            // There is a problem
            if (values.size() > 1 || !values.iterator().next().equals(config.getSuppressedValue())) {
                for(int i = 0; i < hierarchy.length; i++) {
                    hierarchy[i] = Arrays.copyOf(hierarchy[i], hierarchy[i].length + 1);
                    hierarchy[i][hierarchy[i].length - 1] = config.getSuppressedValue();
                }
            }
            
            // Replace
            hierarchies[j] = hierarchy;
            
            // Check
            checkInterrupt();
        }
        
        // Return
        return hierarchies;
    }

    /**
     * Returns indices of quasi-identifiers
     * 
     * @param handle
     * @return
     */
    private int[] getIndicesOfQuasiIdentifiers(DataHandle handle) {
        int[] result = new int[handle.getDefinition().getQuasiIdentifyingAttributes().size()];
        int index = 0;
        for (String qi : handle.getDefinition().getQuasiIdentifyingAttributes()) {
            result[index++] = handle.getColumnIndexOf(qi);
        }
        Arrays.sort(result);
        return result;
    }

    /**
     * Returns the fraction of missing values
     * @param output
     * @param indices
     * @return
     */
    private QualityMeasureColumnOriented getMissings(DataHandle output, int[] indices) {
        
        // Prepare
        double[] minimum = new double[indices.length];
        double[] result = new double[indices.length];
        double[] maximum = new double[indices.length];
        Arrays.fill(minimum, 0d);
        Arrays.fill(maximum, 1d);
        
        // Calculate
        for (int i = 0; i < indices.length; i++) {
            int column = indices[i];
            double missings = 0d;
            for (int row = 0; row < output.getNumRows(); row++) {
                if (output.isOutlier(row) || output.getValue(row, column).equals(DataType.ANY_VALUE) || 
                    output.getValue(row, column).equals(DataType.NULL_VALUE)) {
                    missings += 1d;
                }
                
                // Check
                checkInterrupt();
            } 
            missings /= (double)output.getNumRows();
            result[i] = 1d - missings;
        }

        // Return
        return new QualityMeasureColumnOriented(output, indices, minimum, result, maximum);
    }
}