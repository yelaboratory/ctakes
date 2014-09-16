/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ctakes.assertion.medfacts.cleartk;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;

import org.apache.ctakes.assertion.attributes.features.GenericFeaturesExtractor;
import org.apache.ctakes.assertion.attributes.features.selection.Chi2FeatureSelection;
import org.apache.ctakes.assertion.attributes.features.selection.FeatureSelection;
import org.apache.ctakes.assertion.medfacts.cleartk.extractors.ContextWordWindowExtractor;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.ml.Instance;
import org.cleartk.ml.feature.extractor.FeatureExtractor1;

public class GenericCleartkAnalysisEngine extends
		AssertionCleartkAnalysisEngine {

	boolean USE_DEFAULT_EXTRACTORS = false;
	
	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);
		probabilityOfKeepingADefaultExample = 0.1;
	
//		if (this.isTraining() && this.goldViewName == null) {
//			throw new IllegalArgumentException(PARAM_GOLD_VIEW_NAME + " must be defined during training");
//		}
		
//		if (USE_DEFAULT_EXTRACTORS) {
//			super.initialize(context);
//		} else {
			initialize_generic_extractor();
//		}
			initializeFeatureSelection();

	}

	private void initialize_generic_extractor() throws ResourceInitializationException {
		
//		if (this.contextFeatureExtractors==null) {
//			this.contextFeatureExtractors = new ArrayList<CleartkExtractor>();
//		}
//		this.contextFeatureExtractors.add( 
//				new CleartkExtractor(
//						IdentifiedAnnotation.class, new GenericFeaturesExtractor()) );
		if(this.entityFeatureExtractors == null){
			this.entityFeatureExtractors = new ArrayList<FeatureExtractor1>();
		}
		this.entityFeatureExtractors.add(new ContextWordWindowExtractor("org/apache/ctakes/assertion/models/generic.txt"));
		this.entityFeatureExtractors.add(new GenericFeaturesExtractor());
	}
	
	@Override
	public void setClassLabel(IdentifiedAnnotation entityOrEventMention,
			Instance<String> instance) throws AnalysisEngineProcessException {
		if (this.isTraining())
	      {
	        String generic = entityOrEventMention.getGeneric()? "1":"0";

	        // downsampling. initialize probabilityOfKeepingADefaultExample to 1.0 for no downsampling
	        if ("0".equals(generic) 
	        		&& coin.nextDouble() >= this.probabilityOfKeepingADefaultExample) {
	        	return;
	        }
	        instance.setOutcome(generic);
//	        this.dataWriter.write(instance);
	      } else
	      {
	        String label = this.classifier.classify(instance.getFeatures());
	        entityOrEventMention.setGeneric("1".equals(label));
	      }
	}
	public static FeatureSelection<String> createFeatureSelection(double threshold) {
		return new Chi2FeatureSelection<String>(AssertionCleartkAnalysisEngine.FEATURE_SELECTION_NAME, threshold, false);
		//		  return new MutualInformationFeatureSelection<String>(AssertionCleartkAnalysisEngine.FEATURE_SELECTION_NAME);
	}

	public static URI createFeatureSelectionURI(File outputDirectoryName) {
		return new File(outputDirectoryName, FEATURE_SELECTION_NAME + "_Chi2_extractor.dat").toURI();
	}
	  
	@Override
	protected void initializeFeatureSelection() throws ResourceInitializationException {
	    if (featureSelectionThreshold == 0) {
	    	this.featureSelection = null;
	    } else {
	    	this.featureSelection = this.createFeatureSelection(this.featureSelectionThreshold);

//	    	if ( (new File(this.featureSelectionURI)).exists() ) {
//	    		try {
//	    			this.featureSelection.load(this.featureSelectionURI);
//	    		} catch (IOException e) {
//	    			throw new ResourceInitializationException(e);
//	    		}
//	    	}
	    }		
	}
	  
}
