package tr.com.srdc.cda2fhir.impl;

import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.*;
import ca.uhn.fhir.model.dstu2.resource.Bundle.Entry;
import ca.uhn.fhir.model.dstu2.resource.Encounter;
import ca.uhn.fhir.model.dstu2.resource.Patient;
import ca.uhn.fhir.model.primitive.IdDt;
import org.openhealthtools.mdht.uml.cda.Section;
import org.openhealthtools.mdht.uml.cda.SubstanceAdministration;
import org.openhealthtools.mdht.uml.cda.consol.*;
import tr.com.srdc.cda2fhir.CCDATransformer;
import tr.com.srdc.cda2fhir.ResourceTransformer;

import java.util.UUID;

/**
 * Created by mustafa on 8/3/2016.
 */
public class CCDATransformerImpl implements CCDATransformer {

    private int counter;
    private IdGeneratorEnum idGenerator;
    private ResourceTransformer resTransformer;
    private IdDt patientId;

    public CCDATransformerImpl() {
        this.counter = 0;
        // The default resource id pattern is UUID
        this.idGenerator = IdGeneratorEnum.UUID;
        resTransformer = new ResourceTransformerImpl(this);
    }

    public CCDATransformerImpl(IdGeneratorEnum idGen) {
        this();
        this.idGenerator = idGen;
    }

    @Override
    public void setIdGenerator(IdGeneratorEnum idGen) {
        this.idGenerator = idGen;
    }

    @Override
    public synchronized String getUniqueId() {
        switch (this.idGenerator) {
            case COUNTER:
                return "" + (++counter);
            case UUID:
            default:
                return UUID.randomUUID().toString();
        }
    }

    @Override
    public IdDt getPatientId() {
        return patientId;
    }

    @Override
    public Bundle transformCCD(ContinuityOfCareDocument ccd) {
        if(ccd == null)
            return null;

        // create and init the global bundle and the composition resources
        Bundle ccdBundle = new Bundle();
        Composition ccdComposition = new Composition();
        ccdComposition.setId(new IdDt("Composition", getUniqueId()));
        ccdBundle.addEntry(new Bundle.Entry().setResource(ccdComposition));

        // transform the patient data and assign it to Composition.subject
        // start of bundle-to-patient
        // Since the methods of resTransformer class have return type Bundle, we need the following lines to get the 'Patient' from the 'Bundle'
        Patient subject = null; // subject will be assigned to the appropriate entry of the bundle, we may need null-check
        Bundle subjectBundle = resTransformer.PatientRole2Patient(ccd.getRecordTargets().get(0).getPatientRole());
		for( Entry entry : subjectBundle.getEntry() ){
			if( entry.getResource() instanceof ca.uhn.fhir.model.dstu2.resource.Patient){
				subject = (ca.uhn.fhir.model.dstu2.resource.Patient) entry.getResource();
			}
		}
		
        patientId = subject.getId();
        ccdComposition.setSubject(new ResourceReferenceDt(patientId));
        ccdBundle.addEntry(new Bundle.Entry().setResource(subject));

        for(Section cdaSec: ccd.getSections()) {
            Composition.Section fhirSec = resTransformer.section2Section(cdaSec);
            ccdComposition.addSection(fhirSec);
            if(cdaSec instanceof AdvanceDirectivesSection) {

            }
            else if(cdaSec instanceof AllergiesSection) {
            	AllergiesSection allSec = (AllergiesSection) cdaSec;
            	for(AllergyProblemAct probAct : allSec.getAllergyProblemActs()) {
            		Bundle allBundle = resTransformer.AllergyProblemAct2AllergyIntolerance(probAct);
                    mergeBundles(allBundle, ccdBundle, fhirSec, AllergyIntolerance.class);
            	}
            }
            else if(cdaSec instanceof EncountersSection) {

            }
            else if(cdaSec instanceof FamilyHistorySection) {

            }
            else if(cdaSec instanceof FunctionalStatusSection) {

            }
            else if(cdaSec instanceof ImmunizationsSection) {
            	ImmunizationsSection immSec = (ImmunizationsSection) cdaSec;
            	for(SubstanceAdministration subAd : immSec.getSubstanceAdministrations()) {
            		Bundle immBundle = resTransformer.SubstanceAdministration2Immunization(subAd);
                    mergeBundles(immBundle, ccdBundle, fhirSec, Immunization.class);
            	}
            }
            else if(cdaSec instanceof MedicalEquipmentSection) {

            }
            else if(cdaSec instanceof MedicationsSection) {

            }
            else if(cdaSec instanceof PayersSection) {

            }
            else if(cdaSec instanceof PlanOfCareSection) {

            }
            else if(cdaSec instanceof ProblemSection) {
                ProblemSection probSec = (ProblemSection) cdaSec;
                for(ProblemConcernAct pcAct: probSec.getConsolProblemConcerns()) {
                    Bundle conBundle = resTransformer.ProblemConcernAct2Condition(pcAct);
                    mergeBundles(conBundle, ccdBundle, fhirSec, Condition.class);
                }
            }
            else if(cdaSec instanceof ProceduresSection) {
                ProceduresSection procSec = (ProceduresSection) cdaSec;
                for(ProcedureActivityProcedure proc: procSec.getConsolProcedureActivityProcedures()) {
                    Bundle procBundle = resTransformer.Procedure2Procedure(proc);
                    mergeBundles(procBundle, ccdBundle, fhirSec, ca.uhn.fhir.model.dstu2.resource.Procedure.class);
                }
            }
            else if(cdaSec instanceof ResultsSection) {
            	ResultsSection resultSec = (ResultsSection) cdaSec;
            	for(ResultOrganizer resOrg : resultSec.getResultOrganizers()) {
            		for(ResultObservation resObs : resOrg.getResultObservations()) {
            			Bundle resBundle = resTransformer.ResultObservation2Observation(resObs);
                        mergeBundles(resBundle, ccdBundle, fhirSec, Observation.class);
            		}
            	}
            }
            else if(cdaSec instanceof SocialHistorySection) {

            }
            else if(cdaSec instanceof VitalSignsSection) {
            	VitalSignsSection vitalSec = (VitalSignsSection) cdaSec;
            	for(VitalSignsOrganizer vsOrg : vitalSec.getVitalSignsOrganizers())	{
            		for(VitalSignObservation vsObs : vsOrg.getVitalSignObservations()) {
            			Bundle vsBundle = resTransformer.VitalSignObservation2Observation(vsObs);
                        mergeBundles(vsBundle, ccdBundle, fhirSec, Observation.class);
            		}
            	}
            }
        }

        return ccdBundle;
    }

    /**
     * Copies all the entries from the source bundle to the target bundle, and at the same time adds a reference to the Section.Entry for each instance of the specified class
     * @param sourceBundle
     * @param targetBundle
     * @param fhirSec
     * @param sectionRefCls
     */
    private void mergeBundles(Bundle sourceBundle, Bundle targetBundle, Composition.Section fhirSec, Class<?> sectionRefCls) {
        for(Entry entry : sourceBundle.getEntry()) {
            // Add all the resources returned from the source bundle to the target bundle
            targetBundle.addEntry(new Bundle.Entry().setResource(entry.getResource()));
            // Add a reference to the section for each instance of requested class, e.g. Observation, Procedure ...
            if(sectionRefCls.isInstance(entry.getResource())) {
                ResourceReferenceDt ref = fhirSec.addEntry();
                ref.setReference(entry.getResource().getId());
            }
        }
    }
}
