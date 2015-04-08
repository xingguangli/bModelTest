package beast.math.distributions;


import java.util.Arrays;
import java.util.List;
import java.util.Random;

import beast.core.Description;
import beast.core.Function;
import beast.core.Input;
import beast.core.State;
import beast.core.Input.Validate;
import beast.core.parameter.IntegerParameter;
import beast.core.util.Log;
import beast.evolution.substitutionmodel.NucleotideRevJumpSubstModel;
import beast.math.GammaFunction;

@Description("Prior on rates for reversible jump based substitution model, one of "
		+ "1. Dirichlet prior on rates ensuring they sum to 6. "
		+ "2. Parametric distbution on rates. "
		+ "3. Parametric distributions on rates distinguishing between transitions and transversions.")
public class NucleotideRevJumpSubstModelRatePrior extends Prior {
	public enum BMTPriorType {asScaledDirichlet, onRates, onTransitionsAndTraversals};

	public Input<BMTPriorType> priorTypeInput = new Input<NucleotideRevJumpSubstModelRatePrior.BMTPriorType>("priorType", "rate prior, one of " + Arrays.toString(BMTPriorType.values()), 
			BMTPriorType.onTransitionsAndTraversals, BMTPriorType.values());
	public Input<IntegerParameter> modelIndicatorInput = new Input<IntegerParameter>("modelIndicator", "number of the model to be used", Validate.REQUIRED);
	public Input<NucleotideRevJumpSubstModel> substModelInput = new Input<NucleotideRevJumpSubstModel>("substModel", "model test substitution model representing the individual models", Validate.REQUIRED);

    public Input<ParametricDistribution> transDistInput = new Input<ParametricDistribution>("transDistr", "distribution used to calculate prior on transition rates.");
	
	final static boolean debug = true;
    
    public NucleotideRevJumpSubstModelRatePrior() {
		distInput.setRule(Validate.OPTIONAL);
	}
    
	IntegerParameter modelIndicator;
	NucleotideRevJumpSubstModel substModel;
	Function rates;
	BMTPriorType priorType;
	ParametricDistribution transDist;
	
	@Override
	public void initAndValidate() throws Exception {
		priorType = priorTypeInput.get();
		dist = distInput.get();
		transDist = transDistInput.get();
		
		switch (priorType) {
		case onTransitionsAndTraversals:
			if (transDist == null) {
				Log.warning.println("Setting transitions rate prior to log-normal(1, 1.25)");
				transDist = new LogNormalDistributionModel();
				transDist.initByName("M", "1.0", "S", "1.25");
			}
			if (dist == null) {
				Log.warning.println("Setting transversion rate prior to exponential(1)");
				dist = new Exponential();
			}
			break;
		case onRates:
			if (dist == null) {
				Log.warning.println("Setting rate prior to exponential(1)");
				dist = new Exponential();
			}
			break;
		default:
		}
		
		modelIndicator = modelIndicatorInput.get();
		substModel = substModelInput.get();
		rates = m_x.get();
	}

	@Override
	public double calculateLogP() throws Exception {
		logP = 0;
		if (debug) {
			Function x = m_x.get();
			double sr = 0;
			int modelID = modelIndicator.getValue();
			int dim = (int) substModel.getGroupCount(modelID);
			for (int i = 0; i < dim; i++) {
				sr += substModel.getSubGroupCount(modelID)[i] * x.getArrayValue(i);
			}
			if (Math.abs(sr - 6.0) > 1e-6) {
				throw new RuntimeException("Rates do not add to 6.00000");
			}
		}

		
		
		switch (priorType) {
		case asScaledDirichlet: 
			{
				int modelID = modelIndicator.getValue();
				int K = substModel.getGroupCount(modelID);
				logP += GammaFunction.lnGamma(K);
				for (int i = 0; i < K; i++) {
					logP += Math.log(substModel.getSubGroupCount(modelID)[i]);
				}
				logP -= K * Math.log(6);
			}
			break;
		case onRates:
			{
				Function x = m_x.get();
				int modelID = modelIndicator.getValue();
				int dim = (int) substModel.getGroupCount(modelID);
				double fOffset = dist.offsetInput.get();
				logP = 0;
				for (int i = 0; i < dim; i++) {
					double fX = x.getArrayValue(i) - fOffset;
					logP += dist.logDensity(fX);
				}
			}
			break;
		case onTransitionsAndTraversals:
			{
				Function x = m_x.get();
				int modelID = modelIndicator.getValue();
				int [] model = substModel.getModel(modelID);
				int dim = (int) substModel.getGroupCount(modelID);
				logP = 0;
				for (int i = 0; i < dim; i++) {
					if (model[1] == i || model[4] == i) {
						double fOffset = transDist.offsetInput.get();
						double fX = x.getArrayValue(i) - fOffset;
						logP += transDist.logDensity(fX);
					} else {
						double fOffset = dist.offsetInput.get();
						double fX = x.getArrayValue(i) - fOffset;
						logP += dist.logDensity(fX);
					}
				}
			}
			break;
		}
		return logP;
	}
	
	@Override
	public List<String> getArguments() {return null;}

	@Override
	public List<String> getConditions() {return null;}

	@Override
	public void sample(State state, Random random) {}

}