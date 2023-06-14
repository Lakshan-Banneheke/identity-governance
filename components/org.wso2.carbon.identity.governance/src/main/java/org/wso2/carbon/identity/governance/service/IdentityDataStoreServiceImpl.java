package org.wso2.carbon.identity.governance.service;

import org.apache.commons.lang.StringUtils;
import org.wso2.carbon.identity.base.IdentityException;
import org.wso2.carbon.identity.core.model.IdentityErrorMsgContext;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.governance.internal.IdentityMgtServiceDataHolder;
import org.wso2.carbon.identity.governance.model.UserIdentityClaim;
import org.wso2.carbon.identity.governance.store.UserIdentityDataStore;
import org.wso2.carbon.identity.governance.store.UserStoreBasedIdentityDataStore;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.UserStoreManager;
import org.wso2.carbon.user.core.model.ExpressionCondition;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the IdentityDataStoreService.
 */
public class IdentityDataStoreServiceImpl implements IdentityDataStoreService {

    private UserIdentityDataStore identityDataStore;

    private static final String PRE_SET_USER_CLAIM_VALUES = "PreSetUserClaimValues";
    private static final String PRE_USER_ADD_CLAIM_VALUES = "PreAddUserClaimValues";
    private static final String STORE_IDENTITY_CLAIMS = "StoreIdentityClaims";
    private static final String IDENTITY_DATA_STORE_TYPE = "IdentityDataStore.DataStoreType";
    private static final String DEFAULT_JDBC_IDENTITY_DATA_STORE = "org.wso2.carbon.identity.governance.store" +
            ".JDBCIdentityDataStore";
    private static final String DEFAULT_USER_STORE_BASED_IDENTITY_DATA_STORE = "org.wso2.carbon.identity.governance" +
            ".store.UserStoreBasedIdentityDataStore";

    public IdentityDataStoreServiceImpl() throws ClassNotFoundException, InstantiationException,
            IllegalAccessException {

        String storeClassName = IdentityUtil.getProperty(IDENTITY_DATA_STORE_TYPE);
        if (DEFAULT_JDBC_IDENTITY_DATA_STORE.equals(storeClassName)) {
            identityDataStore = IdentityMgtServiceDataHolder.getInstance().getJDBCIdentityDataStore();
        } else if (DEFAULT_USER_STORE_BASED_IDENTITY_DATA_STORE.equals(storeClassName)) {
            identityDataStore = IdentityMgtServiceDataHolder.getInstance().getUserStoreBasedIdentityDataStore();
        } else {
            Class clazz = Class.forName(storeClassName.trim());
            identityDataStore = (UserIdentityDataStore) clazz.newInstance();
        }
    }

    @Override
    public boolean storeInIdentityDataStore(String userName, UserStoreManager userStoreManager, String operationType,
                                            Map<String, String> claims) throws UserStoreException {

        // Check if the user is locked.
        if (PRE_SET_USER_CLAIM_VALUES.equals(operationType)) {
            boolean accountLocked = Boolean.parseBoolean(claims.get(UserIdentityDataStore.ACCOUNT_LOCK));
            if (accountLocked) {
                IdentityErrorMsgContext customErrorMessageContext = new IdentityErrorMsgContext(UserCoreConstants
                        .ErrorCode.USER_IS_LOCKED);
                IdentityUtil.setIdentityErrorMsg(customErrorMessageContext);
            }
        }

        // No need to separately handle if data identityDataStore is user store based.
        if (isUserStoreBasedIdentityDataStore() || isStoreIdentityClaimsInUserStoreEnabled(userStoreManager)) {
            return true;
        }

        // Top level try and finally blocks are used to unset thread local variables.
        try {
            if (!IdentityUtil.threadLocalProperties.get().containsKey(operationType)) {
                IdentityUtil.threadLocalProperties.get().put(operationType, true);

                UserIdentityClaim userIdentityClaim = null;
                if (!StringUtils.equalsIgnoreCase(operationType, PRE_USER_ADD_CLAIM_VALUES)) {
                    // We avoid loading claims for pre user add operations.
                    userIdentityClaim = identityDataStore.load(userName, userStoreManager);
                }

                if (userIdentityClaim == null) {
                    userIdentityClaim = new UserIdentityClaim(userName);
                }

                Iterator<Map.Entry<String, String>> it = claims.entrySet().iterator();

                while (it.hasNext()) {
                    Map.Entry<String, String> claim = it.next();
                    String key = claim.getKey();
                    String value = claim.getValue();
                    if (key.contains(UserCoreConstants.ClaimTypeURIs.IDENTITY_CLAIM_URI_PREFIX)) {
                        userIdentityClaim.setUserIdentityDataClaim(key, value);
                        it.remove();
                    }
                }

                // Storing the identity claims and challenge questions.
                try {
                    identityDataStore.store(userIdentityClaim, userStoreManager);
                } catch (IdentityException e) {
                    throw new UserStoreException(
                            "Error while saving user identityDataStore data for user : " + userName, e);
                }
            }
            return true;
        } finally {
            // Remove thread local variable.
            IdentityUtil.threadLocalProperties.get().remove(operationType);
        }
    }

    @Override
    public UserIdentityClaim getIdentityClaimData(String username, UserStoreManager userStoreManager) {

        return identityDataStore.load(username, userStoreManager);
    }

    @Override
    public List<String> listUsersByClaimURIAndValue(String claimURI, String claimValue,
                                                    UserStoreManager userStoreManager) throws IdentityException {

        return identityDataStore.list(claimURI, claimValue, userStoreManager);
    }

    @Override
    public List<String> listPaginatedUsersByClaimURIAndValue(List<ExpressionCondition> expressionConditions,
                                                             List<String> identityClaimFilteredUserNames, String domain,
                                                             UserStoreManager userStoreManager, int limit,
                                                             int offset) throws IdentityException {

        return identityDataStore.listPaginatedUsersNames(expressionConditions, identityClaimFilteredUserNames, domain,
                userStoreManager, limit, offset);
    }

    @Override
    public void removeIdentityClaims(String username, UserStoreManager userStoreManager) throws IdentityException {

        identityDataStore.remove(username, userStoreManager);
    }

    @Override
    public boolean isUserStoreBasedIdentityDataStore() {

        return identityDataStore instanceof UserStoreBasedIdentityDataStore;
    }

    /**
     * Check weather the given user store has enabled the property "StoreIdentityClaims" to store identity claims
     * in the user store.
     *
     * @param userStoreManager User Store manager.
     * @return Weather identity claims are stored in user store or not.
     */
    private boolean isStoreIdentityClaimsInUserStoreEnabled(UserStoreManager userStoreManager) {

        return Boolean.parseBoolean(userStoreManager.getRealmConfiguration().
                getUserStoreProperty(STORE_IDENTITY_CLAIMS));
    }
}
