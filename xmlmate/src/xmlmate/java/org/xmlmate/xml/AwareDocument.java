package org.xmlmate.xml;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.xerces.xs.XSAttributeDeclaration;
import org.apache.xerces.xs.XSAttributeUse;
import org.apache.xerces.xs.XSComplexTypeDefinition;
import org.apache.xerces.xs.XSConstants;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSFacet;
import org.apache.xerces.xs.XSModelGroup;
import org.apache.xerces.xs.XSObjectList;
import org.apache.xerces.xs.XSSimpleTypeDefinition;
import org.apache.xerces.xs.XSTerm;
import org.apache.xerces.xs.XSTypeDefinition;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.xerces.xs.XSParticle;

import com.google.common.collect.Lists;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.State;
import dk.brics.automaton.Transition;
import nu.xom.Attribute;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Node;

public class AwareDocument extends Document {
	private static final Logger logger = LoggerFactory
			.getLogger(AwareDocument.class);
	private final Map<XSElementDeclaration, Set<AwareElement>> eleMap;
	//faezeh
	public boolean mutated = false;
	
	public boolean isMutated() {
		return mutated;
	}

	public void setMutated(boolean mutated) {
		this.mutated = mutated;
	}

	public AwareDocument(AwareDocument other) {
		super(other);
		eleMap = new HashMap<>();
		((AwareElement) getRootElement()).register(eleMap);
	}

	public AwareDocument(AwareElement root) {
		super(root);
		eleMap = new HashMap<>();
		root.register(eleMap);
	}

	private static Set<Transition> usedTransitions(XSSimpleTypeDefinition type,
			String value) {
		if (!type.isDefinedFacet(XSSimpleTypeDefinition.FACET_PATTERN))
			return Collections.emptySet();
		ValueGenerator.generateValue(type);// force the type into cache
		Automaton a = ValueGenerator.getCachedAutomaton(type);
		if (null == a)
			return Collections.emptySet();
		Set<Transition> transitions = new HashSet<>();
		State s = a.getInitialState();
		nextChar: for (char c : Lists.charactersOf(value)) {
			for (Transition t : s.getTransitions())
				if (t.getMin() <= c && c <= t.getMax()) {
					transitions.add(t);
					s = t.getDest();
					continue nextChar;
				}
			// if we come off track, return at least a partial solution
			return transitions;
		}
		assert s.isAccept();
		return transitions;
	}

	public void resetEleMap() {
		eleMap.clear();
	}

	public Set<XSElementDeclaration> getElementDeclarations() {
		return eleMap.keySet();
	}

	public Set<XSAttributeDeclaration> getAttributeDeclarations() {
		HashSet<XSAttributeDeclaration> attrs = new HashSet<>();
		for (Set<AwareElement> elems : eleMap.values()) {
			if (null != elems)
				for (AwareElement e : elems) {
					for (int i = 0; i < e.getAttributeCount(); i++) {
						Attribute attribute = e.getAttribute(i);
						if (attribute instanceof AwareAttribute)
							attrs.add(((AwareAttribute) attribute).getDecl());
					}
				}
		}
		return attrs;
	}

	public Set<Transition> getRegexTransitions() {
		Set<Transition> transitions = new HashSet<>();
		for (Set<AwareElement> elems : eleMap.values()) {
			if (null == elems)
				continue;
			for (AwareElement e : elems) {
				// process simply typed element
				XSElementDeclaration decl = e.getDecl();
				if (null == decl)
					continue;
				XSTypeDefinition type = decl.getTypeDefinition();
				if (type.getTypeCategory() == XSTypeDefinition.SIMPLE_TYPE) {
					transitions.addAll(usedTransitions(
							(XSSimpleTypeDefinition) type, e.getValue()));
				} else {
					XSComplexTypeDefinition ctype = (XSComplexTypeDefinition) type;
					if (ctype.getContentType() == XSComplexTypeDefinition.CONTENTTYPE_SIMPLE)
						transitions.addAll(usedTransitions(
								ctype.getSimpleType(), e.getValue()));
					// process attributes
					for (int i = 0; i < e.getAttributeCount(); i++) {
						Attribute attribute = e.getAttribute(i);
						if (attribute instanceof AwareAttribute) {
							XSAttributeDeclaration attrDecl = ((AwareAttribute) attribute)
									.getDecl();
							if (null != attrDecl)
								transitions.addAll(usedTransitions(
										attrDecl.getTypeDefinition(),
										attribute.getValue()));
						}
					}
				}
			}
		}
		return transitions;
	}

	private AwareElement chooseRandom() {
		XSElementDeclaration key = Randomness.choice(eleMap.keySet());
		assert null != key;
		Set<AwareElement> set = eleMap.get(key);
		assert null != set;
		set.remove(null);
		if (set.isEmpty()) {
			eleMap.remove(key); // lazy cleaning
			return chooseRandom();
		}
		return Randomness.choice(set);
	}

	public boolean mutate() {

		boolean result = chooseRandom().mutate();
	     mutateValue_lowerbound();
		//mutateValue_upperbound();
		 //mutateTypeInteger();
		//mutateTypeString();
		//mutateValue_String();
		// changeAttr();
		 //it is not doneappendAttr();
		//it is not done removeAttr();
		 //changeOrderofChildren();
		 //appendElement();
		 //removeElement();
		// removeElementChildren();

		return result;
	}

	public void muxml() {
		// mutateValue_lowerbound();
	}

	public boolean smallNumericMutation() {
		boolean changed = false;
		while (true) {
			if (Randomness.nextDouble() > 0.65d)
				break;
			AwareElement element = chooseRandom(); // XXX might want to optimize
													// the choice for this use
													// case
			changed |= element.smallNumericMutation();
		}
		return changed;
	}

	@Override
	public Node copy() {
		return new AwareDocument(this);
	}

	/**
	 * Prints the tree in a format that can be pasted <a
	 * href="http://ironcreek.net/phpsyntaxtree/">here</a>.
	 */
	public String phpPrint() {
		Element root = getRootElement();
		if (!(root instanceof AwareElement))
			return "unAware " + toXML();
		StringBuilder builder = new StringBuilder();
		((AwareElement) root).phpPrint(builder);
		return builder.toString();
	}

	public Map<XSElementDeclaration, Set<AwareElement>> getEleMap() {
		return eleMap;
	}

	// Mutation operators

	private void mutateValue_lowerbound() {
		logger.info("in mutation operator");
		setMutated(false);
		int num=0;
		// for each decl in elemap, we check weather the min
		int facetfound=0;
		Set<XSElementDeclaration> keys = eleMap.keySet();
		//logger.info("keys are {}", keys);
		for (int i = 0; i < keys.size(); i++) {
			XSElementDeclaration dec = (XSElementDeclaration) keys.toArray()[i];// get the ith key
			Set<AwareElement> aware_set = eleMap.get(dec);
			//logger.info("decl is {} and its has {} elements", dec, aware_set.size());
			for (int k=0; k<aware_set.size();k++){
				AwareElement actualElement = (AwareElement) aware_set.toArray()[k];
				// get the actual element value 
				//logger.info("actual aware element is {}", actualElement.getValue());
				if (actualElement.getValue()!= null && dec.getTypeDefinition()!=null){
					// access to the type of the element 
					XSTypeDefinition eleType = dec.getTypeDefinition();
					switch (eleType.getTypeCategory()){
						case XSTypeDefinition.SIMPLE_TYPE:
						XSSimpleTypeDefinition stype;
						stype = (XSSimpleTypeDefinition) eleType;
						//define facets to check the constraints
						if (dec.getType()== XSConstants.INTEGER_DT)logger.info("stype is integer");
						XSObjectList facets = (XSObjectList) stype.getFacets();
						
						//logger.info("# of facects  : {}", facets.size());
						facetfound=0;
						// check the facets for each element
						
						for (int f = 0; f < facets.size(); f++) {
							XSFacet facet;
							facet = (XSFacet) facets.item(f);
							//we check whether there is a facet which belongs to integer (aka has mininc or minexc)
							if (facet.getFacetKind()== XSSimpleTypeDefinition.FACET_MINEXCLUSIVE) facetfound++;
							if (facet.getFacetKind()== XSSimpleTypeDefinition.FACET_MININCLUSIVE) facetfound++;
							num = facet.getIntFacetValue();
						}// end of for loop of facet
						if (facetfound>0) {//logger.info("{} is integer kind and has facet value of {}", actualElement, facet.getIntFacetValue());
							// change the value of the element
							//int value = Integer.parseInt(actualElement.getChild(0).getValue());
							//if (int(actualElement.getChild(0).getValue())< num)return;
							int offset = Randomness.nextInt(1, 11);
							//logger.info("current value is {}",actualElement.getChild(0).getValue());
							actualElement.removeChildren();
							actualElement.appendChild(Integer.toString( num- offset));
							setMutated(true);
							
						}// end of if
						break;
						case XSTypeDefinition.COMPLEX_TYPE:	
							XSComplexTypeDefinition comtype;
							comtype = (XSComplexTypeDefinition) eleType;
							
							// logger.info("comp/sim type is {}", com_sim);
							if (comtype.getContentType()== XSComplexTypeDefinition.CONTENTTYPE_SIMPLE){
							XSSimpleTypeDefinition com_sim = comtype.getSimpleType();
							XSObjectList facets1 = (XSObjectList) com_sim.getFacets();
							XSFacet facet;
							facetfound=0;
							for (int f = 0; f < facets1.size(); f++) {
								
								facet = (XSFacet) facets1.item(f);
								//logger.info("COMPLEX-SIMPLE type and facet is {} and its value is  {}",
								//facet.getFacetKind(),facet.getActualFacetValue());
								if (facet.getFacetKind()== XSSimpleTypeDefinition.FACET_MINEXCLUSIVE) facetfound++;
								if (facet.getFacetKind()== XSSimpleTypeDefinition.FACET_MININCLUSIVE) facetfound++;
									num = facet.getIntFacetValue();
							}
							if (facetfound>0) {//logger.info("{} is integer kind and has facet value of {}", actualElement, facet.getIntFacetValue());
								// change the value of the element
								int offset = Randomness.nextInt(1, 11);
								//logger.info("simple content type of complex current value is {}",actualElement.getChild(0).getValue());
								actualElement.removeChildren();
								actualElement.appendChild(Integer.toString( num- offset));
								setMutated(true);
								//logger.info("simple content type of complex new value is {}", actualElement.getValue());							
							}
							}
						break;
					
				}// end of switch
			} // end of if getvalue
				if (isMutated()) return;
			}//end of for
		}//end of first for
		
	}

	public void mutateValue_upperbound() {
		mutated = false;
		int num = 0;
		// for each decl in elemap, we check weather the min
		int facetfound = 0;
		Set<XSElementDeclaration> keys = eleMap.keySet();
		// logger.info("keys are {}", keys);
		for (int i = 0; i < keys.size(); i++) {
			XSElementDeclaration dec = (XSElementDeclaration) keys.toArray()[i];// get
																				// the
																				// ith
																				// key
			Set<AwareElement> aware_set = eleMap.get(dec);
			// logger.info("decl is {} and its has {} elements", dec,
			// aware_set.size());
			for (int k = 0; k < aware_set.size(); k++) {
				AwareElement actualElement = (AwareElement) aware_set.toArray()[k];
				// get the actual element value
				// logger.info("actual aware element is {}",
				// actualElement.getValue());
				if (actualElement.getValue() != null
						&& dec.getTypeDefinition() != null) {
					// access to the type of the element
					XSTypeDefinition eleType = dec.getTypeDefinition();
					switch (eleType.getTypeCategory()) {
					case XSTypeDefinition.SIMPLE_TYPE:
						XSSimpleTypeDefinition stype;
						stype = (XSSimpleTypeDefinition) eleType;
						// define facets to check the constraints
						if (dec.getType() == XSConstants.INTEGER_DT)
							logger.info("stype is integer");
						XSObjectList facets = (XSObjectList) stype.getFacets();

						logger.info("# of facects  : {}", facets.size());
						facetfound = 0;
						// check the facets for each element

						for (int f = 0; f < facets.size(); f++) {
							XSFacet facet;
							facet = (XSFacet) facets.item(f);
							// we check whether there is a facet which belongs
							// to integer (aka has mininc or minexc)
							if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_MAXEXCLUSIVE)
								facetfound++;
							if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_MAXINCLUSIVE)
								facetfound++;
							num = facet.getIntFacetValue();
						}// end of for loop of facet
						if (facetfound > 0) {// logger.info("{} is integer kind and has facet value of {}",
												// actualElement,
												// facet.getIntFacetValue());
							// change the value of the element
							// int value =
							// Integer.parseInt(actualElement.getChild(0).getValue());
							int offset = Randomness.nextInt(1, 11);
							// logger.info("current value is {}",actualElement.getChild(0).getValue());
							actualElement.removeChildren();
							actualElement.appendChild(Integer.toString(num
									- offset));
							mutated = true;
							return;

						}// end of if
						break;
					case XSTypeDefinition.COMPLEX_TYPE:
						XSComplexTypeDefinition comtype;
						comtype = (XSComplexTypeDefinition) eleType;

						// logger.info("comp/sim type is {}", com_sim);
						if (comtype.getContentType() == XSComplexTypeDefinition.CONTENTTYPE_SIMPLE) {
							XSSimpleTypeDefinition com_sim = comtype
									.getSimpleType();
							XSObjectList facets1 = (XSObjectList) com_sim
									.getFacets();
							XSFacet facet;
							facetfound = 0;
							for (int f = 0; f < facets1.size(); f++) {

								facet = (XSFacet) facets1.item(f);
								logger.info(
										"COMPLEX-SIMPLE type and facet is {} and its value is  {}",
										facet.getFacetKind(),
										facet.getActualFacetValue());
								if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_MAXEXCLUSIVE)
									facetfound++;
								if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_MAXINCLUSIVE)
									facetfound++;
								num = facet.getIntFacetValue();
							}
							if (facetfound > 0) {// logger.info("{} is integer kind and has facet value of {}",
													// actualElement,
													// facet.getIntFacetValue());
								// change the value of the element
								int offset = Randomness.nextInt(1, 11);
								logger.info(
										"simple content type of complex current value is {}",
										actualElement.getChild(0).getValue());
								actualElement.removeChildren();
								actualElement.appendChild(Integer.toString(num
										- offset));
								mutated = true;
								logger.info(
										"simple content type of complex new value is {}",
										actualElement.getValue());
								return;
							}
						}
						break;

					}// end of switch
				} // end of if getvalue
			}// end of for
		}// end of first for
	}
	

	public void mutateTypeInteger() { //mutate int to string
		mutated = false;
		int num = 0;
		// for each decl in elemap, we check weather the min
		int facetfound = 0;
		Set<XSElementDeclaration> keys = eleMap.keySet();
		// logger.info("keys are {}", keys);
		for (int i = 0; i < keys.size(); i++) {
			XSElementDeclaration dec = (XSElementDeclaration) keys.toArray()[i];
			Set<AwareElement> aware_set = eleMap.get(dec);
			// logger.info("decl is {} and its has {} elements", dec,
			// aware_set.size());
			for (int k = 0; k < aware_set.size(); k++) {
				AwareElement actualElement = (AwareElement) aware_set.toArray()[k];

				if (actualElement.getValue() != null
						&& dec.getTypeDefinition() != null) {
					// access to the type of the element
					XSTypeDefinition eleType = dec.getTypeDefinition();
					switch (eleType.getTypeCategory()) {
					case XSTypeDefinition.SIMPLE_TYPE:
						XSSimpleTypeDefinition stype;
						stype = (XSSimpleTypeDefinition) eleType;
						// define facets to check the constraints

						XSObjectList facets = (XSObjectList) stype.getFacets();

						// logger.info("# of facects  : {}", facets.size());
						facetfound = 0;
						// check the facets for each element
						// logger.info("element is {}, type {}",actualElement.getValue(),
						// actualElement.getChildElements());
						for (int f = 0; f < facets.size(); f++) {
							XSFacet facet;
							facet = (XSFacet) facets.item(f);
							// logger.info("facet name is : {} and its actual value is {} and lexical value is {}",facetKindToString(facet.getFacetKind()),
							// facet.getActualFacetValue(),
							// facet.getLexicalFacetValue());
							// we check whether there is a facet which belongs
							// to integer (aka has mininc or minexc)
							if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_MAXEXCLUSIVE)
								facetfound++;
							if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_MAXINCLUSIVE)
								facetfound++;
							if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_MINEXCLUSIVE)
								facetfound++;
							if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_MININCLUSIVE)
								facetfound++;
							num = facet.getIntFacetValue();
						}// end of for loop of facet
						if (facetfound > 0) {// logger.info("{} is integer kind and has facet value of {}",
												// actualElement,
												// facet.getIntFacetValue());
							// change the value of the element
							// int value =
							// Integer.parseInt(actualElement.getChild(0).getValue());
							// int offset = Randomness.nextInt(1, 11);
							// logger.info("current value is {}",actualElement.getChild(0).getValue());
							actualElement.removeChildren();
							actualElement.appendChild(ValueGenerator.randomString());
							mutated = true;
							logger.info("mutated");
							return;

						}// end of if
						break;
					case XSTypeDefinition.COMPLEX_TYPE:
						XSComplexTypeDefinition comtype;
						comtype = (XSComplexTypeDefinition) eleType;

						// logger.info("comp/sim type is {}", com_sim);
						if (comtype.getContentType() == XSComplexTypeDefinition.CONTENTTYPE_SIMPLE) {
							XSSimpleTypeDefinition com_sim = comtype
									.getSimpleType();
							XSObjectList facets1 = (XSObjectList) com_sim
									.getFacets();
							XSFacet facet;
							facetfound = 0;
							for (int f = 0; f < facets1.size(); f++) {

								facet = (XSFacet) facets1.item(f);
								// logger.info("COMPLEX-SIMPLE type and facet is {} and its value is  {}",
								// facet.getFacetKind(),facet.getActualFacetValue());
								if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_MAXEXCLUSIVE)
									facetfound++;
								if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_MAXINCLUSIVE)
									facetfound++;
								if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_MINEXCLUSIVE)
									facetfound++;
								if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_MININCLUSIVE)
									facetfound++;
								num = facet.getIntFacetValue();
							}
							if (facetfound > 0) {// logger.info("{} is integer kind and has facet value of {}",
													// actualElement,
													// facet.getIntFacetValue());
								// change the value of the element
								// int offset = Randomness.nextInt(1, 11);
								logger.info(
										"simple content type of complex current value is {}",
										actualElement.getChild(0).getValue());
								actualElement.removeChildren();
								actualElement.appendChild(ValueGenerator.randomString());
								mutated = true;
								// logger.info("simple content type of complex new value is {}",
								// actualElement.getValue());
								logger.info("mutated");
								return;
							}
						}
						break;
					}
				} else
					logger.info("the element does not have a value");
			}
		}
	}

	public void mutateTypeString() { // mutate a string to an int
		mutated = false;
		int num = 0;
		// for each decl in elemap, we check weather the min
		int facetfound = 0;
		Set<XSElementDeclaration> keys = eleMap.keySet();
		// logger.info("keys are {}", keys);
		for (int i = 0; i < keys.size(); i++) {
			XSElementDeclaration dec = (XSElementDeclaration) keys.toArray()[i];
			Set<AwareElement> aware_set = eleMap.get(dec);
			// logger.info("decl is {} and its has {} elements", dec,
			// aware_set.size());
			for (int k = 0; k < aware_set.size(); k++) {
				// logger.info("in for loop #2");
				AwareElement actualElement = (AwareElement) aware_set.toArray()[k];

				if (actualElement.getValue() != null
						&& dec.getTypeDefinition() != null) {
					// access to the type of the element
					XSTypeDefinition eleType = dec.getTypeDefinition();
					switch (eleType.getTypeCategory()) {
					case XSTypeDefinition.SIMPLE_TYPE:
						XSSimpleTypeDefinition stype;
						stype = (XSSimpleTypeDefinition) eleType;
						// define facets to check the constraints
						XSObjectList facets = (XSObjectList) stype.getFacets();
						// logger.info("# of facects  : {}", facets.size());
						facetfound = 0;
						// check the facets for each element
						// logger.info("element is {}, type {}",actualElement.getValue(),
						// actualElement.getChildElements());
						for (int f = 0; f < facets.size(); f++) {
							XSFacet facet;
							facet = (XSFacet) facets.item(f);
							// logger.info("facet name is : {} and its actual value is {} and lexical value is {}",facetKindToString(facet.getFacetKind()),
							// facet.getActualFacetValue(),
							// facet.getLexicalFacetValue());
							// we check whether there is a facet which belongs to string
							if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_WHITESPACE)
								facetfound++;
						}// end of for loop of facet
						if (facetfound > 0) {// logger.info("{} is integer kind and has facet value of {}",
												// actualElement,
												// facet.getIntFacetValue());
							// change the value of the element
							int offset = Randomness.nextInt(1, 11);
							// logger.info("simple content type of complex current value is {}",actualElement.getChild(0).getValue());
							actualElement.removeChildren();
							actualElement.appendChild(Integer.toString(offset));
							mutated = true;
							logger.info("mutated");
							return;

						}// end of if
						break;
					case XSTypeDefinition.COMPLEX_TYPE:
						XSComplexTypeDefinition comtype;
						comtype = (XSComplexTypeDefinition) eleType;

						// logger.info("comp/sim type is {}", com_sim);
						if (comtype.getContentType() == XSComplexTypeDefinition.CONTENTTYPE_SIMPLE) {
							XSSimpleTypeDefinition com_sim = comtype
									.getSimpleType();
							XSObjectList facets1 = (XSObjectList) com_sim
									.getFacets();
							XSFacet facet;
							facetfound = 0;
							for (int f = 0; f < facets1.size(); f++) {

								facet = (XSFacet) facets1.item(f);
								// logger.info("COMPLEX-SIMPLE type and facet is {} and its value is  {}",
								// facet.getFacetKind(),facet.getActualFacetValue());
								if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_WHITESPACE)
									facetfound++;
							}// end of for loop of facet
							if (facetfound > 0) {// logger.info("{} is integer kind and has facet value of {}",
													// actualElement,
													// facet.getIntFacetValue());
								// change the value of the element
								int offset = Randomness.nextInt(1, 11);
								// logger.info("simple content type of complex current value is {}",actualElement.getChild(0).getValue());
								actualElement.removeChildren();
								actualElement.appendChild(Integer
										.toString(offset));
								mutated = true;
								logger.info("mutated");
								// logger.info("simple content type of complex new value is {}",
								// actualElement.getValue());
								return;
							}
						}
						break;
					}
				} else
					logger.info("the element does not have value");
			}
		}

		/*
		 * if (aw.getTypeDefinition() != null) { XSTypeDefinition eleType =
		 * aw.getTypeDefinition();
		 * 
		 * if (eleType.getTypeCategory() == XSTypeDefinition.SIMPLE_TYPE) {
		 * XSSimpleTypeDefinition stype; stype = (XSSimpleTypeDefinition)
		 * eleType;
		 * 
		 * if (stype.getType() == XSConstants.INTEGER_DT) {
		 * 
		 * Set<AwareElement> AwareEl = eleMap.get(aw); for (int k = 0; k <
		 * AwareEl.size(); k++) { logger.info(
		 * "aware element, simple type integer {}", AwareEl.toArray()[k]);
		 * AwareElement actualElement = (AwareElement) AwareEl .toArray()[k];
		 * logger.info("XML element current value is: {}",
		 * actualElement.getValue()); logger.info("number of children {}",
		 * actualElement.getChildCount()); actualElement.removeChild(0);
		 * actualElement .appendChild(actualElement.getValue() + 0.5);
		 * logger.info("new value of element is {}", actualElement.getValue());
		 * }// end for loop }// end if }// end if simple if
		 * (eleType.getTypeCategory() == XSTypeDefinition.COMPLEX_TYPE) {
		 * XSComplexTypeDefinition comtype; comtype = (XSComplexTypeDefinition)
		 * eleType; if (comtype.getContentType() ==
		 * XSComplexTypeDefinition.CONTENTTYPE_SIMPLE) { XSSimpleTypeDefinition
		 * com_sim = comtype .getSimpleType(); logger.info(
		 * "complex type- content simple and its type is {}",
		 * com_sim.getType()); if (com_sim.getType() == XSConstants.INTEGER_DT)
		 * { Set<AwareElement> AwareEl = eleMap.get(aw); for (int k = 0; k <
		 * AwareEl.size(); k++) { logger.info(
		 * "aware element, simple type integer {}", AwareEl.toArray()[k]);
		 * AwareElement actualElement = (AwareElement) AwareEl .toArray()[k];
		 * logger.info("XML element current value is: {}",
		 * actualElement.getValue()); logger.info("number of children {}",
		 * actualElement.getChildCount()); actualElement.removeChild(0);
		 * actualElement.appendChild(actualElement .getValue() + 0.5);
		 * logger.info("new value of element is {}", actualElement.getValue());
		 * }// end for loop }// end if }// end if content simple }// end if
		 * complex }// end if }// end for
		 */}// end of function

	public void mutateValue_String() {
		mutated = false;
		int num = 0;
		// for each decl in elemap, we check weather the min
		int facetfound = 0;
		int minlengthfacet =0;
		int maxlengthfacet=0;
		Set<XSElementDeclaration> keys = eleMap.keySet();
		// logger.info("keys are {}", keys);
		for (int i = 0; i < keys.size(); i++) {
			XSElementDeclaration dec = (XSElementDeclaration) keys.toArray()[i];// get
																				// the
																				// ith
																				// key
			Set<AwareElement> aware_set = eleMap.get(dec);
			// logger.info("decl is {} and its has {} elements", dec,
			// aware_set.size());
			for (int k = 0; k < aware_set.size(); k++) {
				// logger.info("in for loop #2");
				AwareElement actualElement = (AwareElement) aware_set.toArray()[k];

				if (actualElement.getValue() != null
						&& dec.getTypeDefinition() != null) {
					// access to the type of the element
					XSTypeDefinition eleType = dec.getTypeDefinition();
					switch (eleType.getTypeCategory()) {
					case XSTypeDefinition.SIMPLE_TYPE:
						XSSimpleTypeDefinition stype;
						stype = (XSSimpleTypeDefinition) eleType;
						// define facets to check the constraints

						XSObjectList facets = (XSObjectList) stype.getFacets();

						// logger.info("# of facects  : {}", facets.size());
						facetfound = 0;
						minlengthfacet =0;
						maxlengthfacet=0;
						// check the facets for each element
						// logger.info("element is {}, type {}",actualElement.getValue(),
						// actualElement.getChildElements());
						for (int f = 0; f < facets.size(); f++) {
							XSFacet facet;
							facet = (XSFacet) facets.item(f);
							// logger.info("facet name is : {} and its actual value is {} and lexical value is {}",facetKindToString(facet.getFacetKind()),
							// facet.getActualFacetValue(),
							// facet.getLexicalFacetValue());
							// we check whether there is a facet which belongs
							// to integer (aka has mininc or minexc)
							if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_WHITESPACE)
								facetfound++;
							if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_MAXLENGTH){
								maxlengthfacet= facet.getIntFacetValue();
								logger.info("max length is {}", maxlengthfacet);}
							if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_MINLENGTH){
								minlengthfacet= facet.getIntFacetValue();
								logger.info("min length is {}", minlengthfacet);}
							if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_MININCLUSIVE){
								minlengthfacet= facet.getIntFacetValue();
								logger.info("FACET_MININCLUSIVE length is {}", minlengthfacet);}
						}// end of for loop of facet
						if (facetfound > 0) {// logger.info("{} is integer kind and has facet value of {}",
												// actualElement,
												// facet.getIntFacetValue());
							// change the value of the element

							// logger.info("simple content type of complex current value is {}",actualElement.getChild(0).getValue());
							actualElement.removeChildren();
							actualElement.appendChild(ValueGenerator.randomString());
							mutated = true;
							logger.info("mutated");
							return;

						}// end of if
						break;
					case XSTypeDefinition.COMPLEX_TYPE:
						XSComplexTypeDefinition comtype;
						comtype = (XSComplexTypeDefinition) eleType;

						// logger.info("comp/sim type is {}", com_sim);
						if (comtype.getContentType() == XSComplexTypeDefinition.CONTENTTYPE_SIMPLE) {
							XSSimpleTypeDefinition com_sim = comtype
									.getSimpleType();
							XSObjectList facets1 = (XSObjectList) com_sim
									.getFacets();
							XSFacet facet;
							facetfound = 0;
							for (int f = 0; f < facets1.size(); f++) {

								facet = (XSFacet) facets1.item(f);
								// logger.info("COMPLEX-SIMPLE type and facet is {} and its value is  {}",
								// facet.getFacetKind(),facet.getActualFacetValue());
								if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_WHITESPACE)
									facetfound++;
								if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_MAXLENGTH){
									maxlengthfacet= facet.getIntFacetValue();
									logger.info("max length is {}", maxlengthfacet);}
								if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_MINLENGTH){
									minlengthfacet= facet.getIntFacetValue();
									logger.info("min length is {}", minlengthfacet);}
								if (facet.getFacetKind() == XSSimpleTypeDefinition.FACET_MININCLUSIVE){
									minlengthfacet= facet.getIntFacetValue();
									logger.info("FACET_MININCLUSIVE length is {}", minlengthfacet);}
							}// end of for loop of facet
							if (facetfound > 0) {// logger.info("{} is integer kind and has facet value of {}",
													// actualElement,
													// facet.getIntFacetValue());
								// change the value of the element
								// logger.info("simple content type of complex current value is {}",actualElement.getChild(0).getValue());
								actualElement.removeChildren();
								actualElement.appendChild(ValueGenerator.randomString());
								mutated = true;
								logger.info("mutated");
								// logger.info("simple content type of complex new value is {}",
								// actualElement.getValue());
								return;
							}
												}
						break;
					}
				} else
					logger.info("the element does not have value");
			}
		}

	}// end mutateString

	public void removeElementChildren() {
		mutated = false;
		XSElementDeclaration key = Randomness.choice(eleMap.keySet());
		assert null != key;
		Set<AwareElement> set = eleMap.get(key);
		assert null != set;
		AwareElement actualElement = Randomness.choice(set);
		assert null != actualElement;
		logger.info("key is {} and its value is {},  {}", key, actualElement,
				eleMap.get(key));
		XSTypeDefinition eleType = key.getTypeDefinition();
		switch (eleType.getTypeCategory()) {
		case XSTypeDefinition.SIMPLE_TYPE:
			logger.info("simple type");
			break;
		case XSTypeDefinition.COMPLEX_TYPE:
			logger.info("complex type");
			XSComplexTypeDefinition comtype;
			comtype = (XSComplexTypeDefinition) eleType;
			if (comtype.getContentType() == XSComplexTypeDefinition.CONTENTTYPE_ELEMENT) {

				// assert null!=par;
				logger.info("element child {} and its particle is {}",
						actualElement.getChildCount(), comtype.getParticle());
				if (actualElement.getChildCount() > 0) {
					actualElement.removeChildren();
					logger.info("element is removed {}",
							actualElement.getChildCount());
					mutated = true;
				}
			} else {
				logger.info("key is {} and its value is {}", key,
						eleMap.get(key));
				// eleMap.remove(key);
				actualElement.deregister(eleMap);
				logger.info(
						"after removing key is \"{}\" and its value is now {}",
						key, eleMap.get(key));
				mutated = true;
			}
			break;
		}
	}

	public void removeElement() {
		mutated = false;
		XSElementDeclaration key = Randomness.choice(eleMap.keySet());
		assert null != key;
		Set<AwareElement> set = eleMap.get(key);
		assert null != set;
		logger.info("key is {} and its set is {}", key, set);
		if(!set.isEmpty()){
			AwareElement actualElement = Randomness.choice(set);
			assert null != actualElement;
			logger.info("the node is {}, its children are {}", actualElement,
					actualElement.getChildCount());
			if (actualElement.getChildCount()!=0){
				actualElement.removeChild(0);
			
			logger.info("now it is removed , its children are {}",
					actualElement.getChildCount());
			mutated = true;
			}
		}


		// }
	}

	public void appendElement() {
		mutated = false;
		Element root = getRootElement();
		XSElementDeclaration key = Randomness.choice(eleMap.keySet());
		assert null != key;
		Set<AwareElement> set = eleMap.get(key);
		assert null != set;
		AwareElement actualElement = Randomness.choice(set);
		assert null != actualElement;
		//logger.info("actual element is {}",  actualElement);
		if (root!= actualElement && actualElement!=null){
			AwareElement copel = (AwareElement) actualElement.shallowCopy();
			//logger.info("element parent is {} and element's copy is {}",	actualElement.getParent().getChildCount(), copel);
			actualElement.getParent().appendChild(copel);
			copel.register(eleMap);
			//logger.info("now the parent has {}", actualElement.getParent() .getChildCount());
			mutated = true;

		}
		if (mutated)System.out.println("element is mutated.");

	}

	public void changeOrderofChildren() {
		mutated = false;
		XSElementDeclaration key = Randomness.choice(eleMap.keySet());
		assert null != key;
		Set<AwareElement> set = eleMap.get(key);
		assert null != set;
		for (int k = 0; k < set.size(); k++) {
			// logger.info("in for loop #2");
			AwareElement actualElement = (AwareElement) set.toArray()[k];
			
			//AwareElement actualElement = Randomness.choice(set);
			assert null != actualElement;
			logger.info("key is {} and its value is {},  {}", key, actualElement,	eleMap.get(key));
			
			logger.info("the # of childern of this element are ", getChildCount());
			//AwareElement copel = (AwareElement) actualElement.shallowCopy();
			//actualElement.getParent().appendChild(copel);
			//copel.register(eleMap);

				
		}//for

	}

	public void removeAttr() {
		mutated = false;
		Set<XSAttributeDeclaration> awattr = getAttributeDeclarations(); 
		assert null != awattr;
		logger.info("it is attrib decl {}", awattr);
		//get a single attribute declaration
		while(!mutated){
		XSAttributeDeclaration attrdecl = Randomness.choice(awattr);
		assert null != attrdecl;
		logger.info("constraint type is {}", attrdecl.getConstraintType());
		if (attrdecl.getConstraintType()!=XSConstants.VC_NONE){
		logger.info("the constraint valus is {}",attrdecl.getValueConstraintValue().getActualValue());
		 
		mutated = true;}
		}
		logger.info("mutated");

	}

	public void appendAttr() {

		
	}

	public void changeAttr() {

	}

	private static String facetKindToString(short facetKind) {
		switch (facetKind) {
		case XSSimpleTypeDefinition.FACET_NONE:
			return "none";
		case XSSimpleTypeDefinition.FACET_LENGTH:
			return "length";
		case XSSimpleTypeDefinition.FACET_MINLENGTH:
			return "minLength";
		case XSSimpleTypeDefinition.FACET_MAXLENGTH:
			return "maxLength";
		case XSSimpleTypeDefinition.FACET_PATTERN:
			return "pattern";
		case XSSimpleTypeDefinition.FACET_WHITESPACE:
			return "whitespace";
		case XSSimpleTypeDefinition.FACET_MAXINCLUSIVE:
			return "maxInclusive";
		case XSSimpleTypeDefinition.FACET_MAXEXCLUSIVE:
			return "maxExclusive";
		case XSSimpleTypeDefinition.FACET_MINEXCLUSIVE:
			return "minExclusive";
		case XSSimpleTypeDefinition.FACET_MININCLUSIVE:
			return "minInclusive";
		case XSSimpleTypeDefinition.FACET_TOTALDIGITS:
			return "totalDigits";
		case XSSimpleTypeDefinition.FACET_FRACTIONDIGITS:
			return "fractionDigits";
		case XSSimpleTypeDefinition.FACET_ENUMERATION:
			return "enumeration";
		default:
			return "unknown facet kind";
		}
	}

}
