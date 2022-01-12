/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.storage;

import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.management.profile.AzureProfile;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.CheckNameAvailabilityResult;
import com.azure.resourcemanager.storage.models.Reason;
import com.microsoft.azure.toolkit.lib.AzService;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.AzureConfiguration;
import com.microsoft.azure.toolkit.lib.AzureService;
import com.microsoft.azure.toolkit.lib.auth.Account;
import com.microsoft.azure.toolkit.lib.auth.AzureAccount;
import com.microsoft.azure.toolkit.lib.common.entity.CheckNameAvailabilityResultEntity;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.common.model.AzResource;
import com.microsoft.azure.toolkit.lib.common.model.Subscription;
import com.microsoft.azure.toolkit.lib.storage.model.Kind;
import com.microsoft.azure.toolkit.lib.storage.model.Performance;
import com.microsoft.azure.toolkit.lib.storage.model.Redundancy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class AzureStorageAccount extends AbstractAzResourceModule<StorageResourceManager, AzResource.None, StorageManager>
    implements AzService {

    public AzureStorageAccount() {
        super("Microsoft.Storage", AzResource.NONE);
    }

    @Nonnull
    public StorageAccountModule accounts(@Nonnull String subscriptionId) {
        final StorageResourceManager rm = get(subscriptionId, null);
        assert rm != null;
        return rm.getStorageModule();
    }

    public StorageResourceManager forSubscription(@Nonnull String subscriptionId) {
        return this.get(subscriptionId, null);
    }

    @Nonnull
    protected Stream<StorageManager> loadResourcesFromAzure() {
        return Azure.az(AzureAccount.class).account().getSelectedSubscriptions().stream().parallel()
            .map(Subscription::getId).map(i -> loadResourceFromAzure(i, null));
    }

    @Nonnull
    @Override
    protected StorageManager loadResourceFromAzure(@Nonnull String subscriptionId, String resourceGroup) {
        final Account account = Azure.az(AzureAccount.class).account();
        final AzureConfiguration config = Azure.az().config();
        final String userAgent = config.getUserAgent();
        final HttpLogDetailLevel logLevel = Optional.ofNullable(config.getLogLevel()).map(HttpLogDetailLevel::valueOf).orElse(HttpLogDetailLevel.NONE);
        final AzureProfile azureProfile = new AzureProfile(null, subscriptionId, account.getEnvironment());
        return StorageManager.configure()
            .withHttpClient(AzureService.getDefaultHttpClient())
            .withLogLevel(logLevel)
            .withPolicy(AzureService.getUserAgentPolicy(userAgent)) // set user agent with policy
            .authenticate(account.getTokenCredential(subscriptionId), azureProfile);
    }

    @Override
    protected StorageResourceManager newResource(@Nonnull StorageManager remote) {
        return new StorageResourceManager(remote, this);
    }

    @Override
    protected Object getClient() {
        throw new AzureToolkitRuntimeException("not supported");
    }

    @Nonnull
    @Override
    public String toResourceId(@Nonnull String resourceName, String resourceGroup) {
        final String rg = StringUtils.firstNonBlank(resourceGroup, AzResource.RESOURCE_GROUP_PLACEHOLDER);
        return String.format("/subscriptions/%s/resourceGroups/%s/providers/%s", resourceName, rg, this.getName());
    }

    public CheckNameAvailabilityResultEntity checkNameAvailability(String subscriptionId, String name) {
        final StorageManager manager = loadResourceFromAzure(subscriptionId, null);
        CheckNameAvailabilityResult result = manager.storageAccounts().checkNameAvailability(name);
        return new CheckNameAvailabilityResultEntity(result.isAvailable(),
            Optional.ofNullable(result.reason()).map(Reason::toString).orElse(null), result.message());
    }

    public List<Performance> listSupportedPerformances() {
        return Performance.values();
    }

    public List<Kind> listSupportedKinds(@Nonnull Performance performance) {
        return Kind.values().stream().filter(k -> Objects.equals(k.getPerformance(), performance)).collect(Collectors.toList());
    }

    public List<Redundancy> listSupportedRedundancies(@Nonnull Performance performance, @Nullable Kind kind) {
        return Redundancy.values().stream()
                .filter(r -> Objects.equals(r.getPerformance(), performance))
                .filter(r -> !(Objects.equals(Kind.PAGE_BLOB_STORAGE, kind) && Objects.equals(r, Redundancy.PREMIUM_ZRS)))
                .collect(Collectors.toList());
    }
}