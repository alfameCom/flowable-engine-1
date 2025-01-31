/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flowable.cmmn.engine.impl.history;

import java.util.Collection;
import java.util.List;

import org.flowable.cmmn.engine.CmmnEngineConfiguration;
import org.flowable.cmmn.engine.impl.persistence.entity.HistoricCaseInstanceEntity;
import org.flowable.cmmn.engine.impl.persistence.entity.HistoricCaseInstanceEntityManager;
import org.flowable.cmmn.engine.impl.persistence.entity.HistoricMilestoneInstanceEntityManager;
import org.flowable.cmmn.engine.impl.persistence.entity.HistoricPlanItemInstanceEntityManager;
import org.flowable.cmmn.engine.impl.task.TaskHelper;
import org.flowable.common.engine.api.scope.ScopeTypes;
import org.flowable.identitylink.service.HistoricIdentityLinkService;
import org.flowable.variable.service.impl.persistence.entity.HistoricVariableInstanceEntity;
import org.flowable.variable.service.impl.persistence.entity.HistoricVariableInstanceEntityManager;

/**
 * Contains logic that is shared by multiple classes around history.
 * 
 * @author Joram Barrez
 */
public class CmmnHistoryHelper {
    
    public static void deleteHistoricCaseInstance(CmmnEngineConfiguration cmmnEngineConfiguration, String caseInstanceId) {
        HistoricCaseInstanceEntityManager historicCaseInstanceEntityManager = cmmnEngineConfiguration.getHistoricCaseInstanceEntityManager();
        HistoricCaseInstanceEntity historicCaseInstance = historicCaseInstanceEntityManager.findById(caseInstanceId);

        HistoricMilestoneInstanceEntityManager historicMilestoneInstanceEntityManager = cmmnEngineConfiguration.getHistoricMilestoneInstanceEntityManager();
        historicMilestoneInstanceEntityManager.findHistoricMilestoneInstancesByQueryCriteria(new HistoricMilestoneInstanceQueryImpl().milestoneInstanceCaseInstanceId(historicCaseInstance.getId()))
                .forEach(m -> historicMilestoneInstanceEntityManager.delete(m.getId()));

        HistoricPlanItemInstanceEntityManager historicPlanItemInstanceEntityManager = cmmnEngineConfiguration.getHistoricPlanItemInstanceEntityManager();
        historicPlanItemInstanceEntityManager.findByCriteria(new HistoricPlanItemInstanceQueryImpl().planItemInstanceCaseInstanceId(historicCaseInstance.getId()))
                .forEach(p -> historicPlanItemInstanceEntityManager.delete(p.getId()));

        HistoricIdentityLinkService historicIdentityLinkService = cmmnEngineConfiguration.getIdentityLinkServiceConfiguration().getHistoricIdentityLinkService();
        historicIdentityLinkService.deleteHistoricIdentityLinksByScopeIdAndScopeType(historicCaseInstance.getId(), ScopeTypes.CMMN);
        historicIdentityLinkService.deleteHistoricIdentityLinksByScopeIdAndScopeType(historicCaseInstance.getId(), ScopeTypes.PLAN_ITEM);
        
        if (cmmnEngineConfiguration.isEnableEntityLinks()) {
            cmmnEngineConfiguration.getEntityLinkServiceConfiguration().getHistoricEntityLinkService()
            .deleteHistoricEntityLinksByScopeIdAndScopeType(historicCaseInstance.getId(), ScopeTypes.CMMN);
        }

        HistoricVariableInstanceEntityManager historicVariableInstanceEntityManager = cmmnEngineConfiguration.getVariableServiceConfiguration().getHistoricVariableInstanceEntityManager();
        List<HistoricVariableInstanceEntity> historicVariableInstanceEntities = historicVariableInstanceEntityManager
            .findHistoricalVariableInstancesByScopeIdAndScopeType(caseInstanceId, ScopeTypes.CMMN);
        for (HistoricVariableInstanceEntity historicVariableInstanceEntity : historicVariableInstanceEntities) {
            historicVariableInstanceEntityManager.delete(historicVariableInstanceEntity);
        }

        TaskHelper.deleteHistoricTaskInstancesByCaseInstanceId(caseInstanceId, cmmnEngineConfiguration);

        historicCaseInstanceEntityManager.delete(historicCaseInstance);

        // Also delete any sub cases that may be active
        historicCaseInstanceEntityManager.createHistoricCaseInstanceQuery().caseInstanceParentId(caseInstanceId).list()
                .forEach(c -> deleteHistoricCaseInstance(cmmnEngineConfiguration, c.getId()));
    }
    
    public static void bulkDeleteHistoricCaseInstances(Collection<String> caseInstanceIds, CmmnEngineConfiguration cmmnEngineConfiguration) {
        HistoricCaseInstanceEntityManager historicCaseInstanceEntityManager = cmmnEngineConfiguration.getHistoricCaseInstanceEntityManager();

        HistoricMilestoneInstanceEntityManager historicMilestoneInstanceEntityManager = cmmnEngineConfiguration.getHistoricMilestoneInstanceEntityManager();
        historicMilestoneInstanceEntityManager.bulkDeleteHistoricMilestoneInstancesForCaseInstanceIds(caseInstanceIds);

        HistoricPlanItemInstanceEntityManager historicPlanItemInstanceEntityManager = cmmnEngineConfiguration.getHistoricPlanItemInstanceEntityManager();
        historicPlanItemInstanceEntityManager.bulkDeleteHistoricPlanItemInstancesForCaseInstanceIds(caseInstanceIds);

        HistoricIdentityLinkService historicIdentityLinkService = cmmnEngineConfiguration.getIdentityLinkServiceConfiguration().getHistoricIdentityLinkService();
        historicIdentityLinkService.bulkDeleteHistoricIdentityLinksByScopeIdsAndScopeType(caseInstanceIds, ScopeTypes.CMMN);
        historicIdentityLinkService.bulkDeleteHistoricIdentityLinksByScopeIdsAndScopeType(caseInstanceIds, ScopeTypes.PLAN_ITEM);
        
        if (cmmnEngineConfiguration.isEnableEntityLinks()) {
            cmmnEngineConfiguration.getEntityLinkServiceConfiguration().getHistoricEntityLinkService()
                    .bulkDeleteHistoricEntityLinksForScopeTypeAndScopeIds(ScopeTypes.CMMN, caseInstanceIds);
        }

        HistoricVariableInstanceEntityManager historicVariableInstanceEntityManager = cmmnEngineConfiguration.getVariableServiceConfiguration().getHistoricVariableInstanceEntityManager();
        historicVariableInstanceEntityManager.bulkDeleteHistoricVariableInstancesByScopeIdsAndScopeType(caseInstanceIds, ScopeTypes.CMMN);

        TaskHelper.bulkDeleteHistoricTaskInstancesByCaseInstanceIds(caseInstanceIds, cmmnEngineConfiguration);

        historicCaseInstanceEntityManager.bulkDeleteHistoricCaseInstances(caseInstanceIds);

        // Also delete any sub cases that may be active
        List<String> subCaseInstanceIds = historicCaseInstanceEntityManager.findHistoricCaseInstanceIdsByParentIds(caseInstanceIds);
        if (subCaseInstanceIds != null && !subCaseInstanceIds.isEmpty()) {
            bulkDeleteHistoricCaseInstances(subCaseInstanceIds, cmmnEngineConfiguration);
        }
    }

}
