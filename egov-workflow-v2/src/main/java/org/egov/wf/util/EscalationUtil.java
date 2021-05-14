package org.egov.wf.util;

import com.jayway.jsonpath.JsonPath;
import org.egov.tracer.model.CustomException;
import org.egov.wf.service.BusinessMasterService;
import org.egov.wf.web.models.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static org.egov.wf.util.WorkflowConstants.JSONPATH_AUTOESCALTION;

@Component
public class EscalationUtil {


    private BusinessMasterService businessMasterService;


    @Autowired
    public EscalationUtil(BusinessMasterService businessMasterService) {
        this.businessMasterService = businessMasterService;
    }


    /**
     * Generates processInstance object for each businessId
     * @param tenantId
     * @param businessIds
     * @param escalation
     * @return
     */
    public List<ProcessInstance> getProcessInstances(String tenantId, List<String> businessIds, Escalation escalation){

        List<ProcessInstance> processInstances = new LinkedList<>();

        for(String businessId : businessIds){
            ProcessInstance processInstance = ProcessInstance.builder().businessId(businessId)
                                                .action(escalation.getAction())
                                                .businessService(escalation.getBusinessService())
                                                .moduleName(escalation.getModuleName())
                                                .tenantId(tenantId)
                                                .build();
            processInstances.add(processInstance);
        }

        return processInstances;

    }


    /**
     * Enriches the state uuid based on the statusCode sent
     * @param statusCode
     * @param criteria
     */
    public void enrichStatusUUID(String statusCode, EscalationSearchCriteria criteria){

        String tenantId = criteria.getTenantId();
        String businessService = criteria.getBusinessService();

        BusinessServiceSearchCriteria businessServiceSearchCriteria = new BusinessServiceSearchCriteria();
        businessServiceSearchCriteria.setTenantIds(Collections.singletonList(tenantId));
        businessServiceSearchCriteria.setBusinessServices(Collections.singletonList(businessService));

        List<BusinessService> businessServices = businessMasterService.search(businessServiceSearchCriteria);

        if(!CollectionUtils.isEmpty(businessServices)){
            throw new CustomException("BUSINESSSERVICE_NOT_FOUND","No BusinessService found for tenantId: "+tenantId+" and code: "+businessService);
        }

        String uuid = null;

        for(State state : businessServices.get(0).getStates()) {
            if(state.getState().equalsIgnoreCase(statusCode)){
                uuid = state.getUuid();
                break;
            }
        }

        if(uuid == null){
            throw new CustomException("STATUS_NOT_FOUND","No uuid found for tenantId: "+tenantId+" and status: "+statusCode);
        }

        criteria.setStatus(uuid);

    }


    /**
     * Converts days to millisecond
     * @param day
     * @return
     */
    public Long daysToMillisecond(Double day){
        return Double.valueOf(day*24*60*60*1000).longValue();
    }


    /**
     * Get Escalations for the given businessService from config
     * @param businessService
     * @return
     */
    public List<Escalation> getEscalationsFromConfig(String businessService, Object mdmsData){

        List<HashMap<String, Object>> configs = JsonPath.read(mdmsData,JSONPATH_AUTOESCALTION);
        HashMap<String, String> errorMap = new HashMap<>();
        List<Escalation> escalations = new LinkedList<>();

        for(HashMap map : configs){
            String configBusinessService = (String) map.get("businessService");

            if(!configBusinessService.equalsIgnoreCase(businessService))
                continue;

            String state  = (String) map.get("state");
            String action  = (String) map.get("action");
            String module  = (String) map.get("module");
            Long  stateSla = (Long) map.get("stateSLA");
            Long  businessSLa = (Long) map.get("businessSLA");

            if(stateSla == null && businessSLa == null)
                errorMap.put("INVALID_CONFIG","Both stateSLA and businessSLA are null for config with state: "+state+" and action: "+action);

            Escalation escalation = Escalation.builder().action(action).status(state)
                                    .businessService(businessService)
                                    .businessSlaExceededBy(businessSLa)
                                    .stateSlaExceededBy(stateSla)
                                    .moduleName(module)
                                    .build();

            escalations.add(escalation);
        }

        if(!errorMap.isEmpty())
            throw new CustomException(errorMap);

        return escalations;
    }



}
