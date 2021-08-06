/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.storage.service;


import com.azure.resourcemanager.resources.fluentcore.arm.ResourceId;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.CheckNameAvailabilityResult;
import com.azure.resourcemanager.storage.models.Kind;
import com.azure.resourcemanager.storage.models.Reason;
import com.azure.resourcemanager.storage.models.SkuName;
import com.azure.resourcemanager.storage.models.StorageAccountSkuType;
import com.microsoft.azure.toolkit.lib.AzureService;
import com.microsoft.azure.toolkit.lib.SubscriptionScoped;
import com.microsoft.azure.toolkit.lib.common.entity.CheckNameAvailabilityResultEntity;
import com.microsoft.azure.toolkit.lib.common.event.AzureOperationEvent;
import com.microsoft.azure.toolkit.lib.common.model.Subscription;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.common.task.ICommittable;
import com.microsoft.azure.toolkit.lib.storage.StorageManagerFactory;
import com.microsoft.azure.toolkit.lib.storage.model.KindEnum;
import com.microsoft.azure.toolkit.lib.storage.model.PerformanceEnum;
import com.microsoft.azure.toolkit.lib.storage.model.RedundancyEnum;
import com.microsoft.azure.toolkit.lib.storage.model.StorageAccountConfig;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class AzureStorageAccount extends SubscriptionScoped<AzureStorageAccount> implements AzureService {

    public AzureStorageAccount() {
        super(AzureStorageAccount::new);
    }

    private AzureStorageAccount(@Nonnull final List<Subscription> subscriptions) {
        super(AzureStorageAccount::new, subscriptions);
    }

    public List<StorageAccount> list() {
        return getSubscriptions().stream()
            .map(subscription -> StorageManagerFactory.create(subscription.getId()))
            .flatMap(manager -> manager.storageAccounts().list().stream())
            .collect(Collectors.toList()).stream()
            .map(a -> new StorageAccount(a.manager(), a))
            .collect(Collectors.toList());
    }

    public StorageAccount get(@Nonnull String id) {
        final com.azure.resourcemanager.storage.models.StorageAccount account =
                StorageManagerFactory.create(ResourceId.fromString(id).subscriptionId()).storageAccounts().getById(id);
        return new StorageAccount(account.manager(), account);
    }

    public StorageAccount get(@Nonnull final String resourceGroup, @Nonnull final String name) {
        final com.azure.resourcemanager.storage.models.StorageAccount account =
                StorageManagerFactory.create(getDefaultSubscription().getId()).storageAccounts().getByResourceGroup(resourceGroup, name);
        return new StorageAccount(account.manager(), account);
    }

    public CheckNameAvailabilityResultEntity checkNameAvailability(String subscriptionId, String name) {
        StorageManager manager = StorageManagerFactory.create(subscriptionId);
        CheckNameAvailabilityResult result = manager.storageAccounts().checkNameAvailability(name);
        return new CheckNameAvailabilityResultEntity(result.isAvailable(),
                Optional.ofNullable(result.reason()).map(Reason::toString).orElse(null), result.message());
    }

    public List<KindEnum> listSupportedKinds() {
        return Arrays.stream(KindEnum.values()).collect(Collectors.toList());
    }

    public List<PerformanceEnum> listSupportedPerformances() {
        return Arrays.stream(PerformanceEnum.values()).collect(Collectors.toList());
    }

    public List<RedundancyEnum> listSupportedReplicationsByPerformance(@Nonnull PerformanceEnum performance) {
        return Arrays.stream(RedundancyEnum.values())
                .filter(r -> Objects.equals(r.getPerformance(), performance))
                .collect(Collectors.toList());
    }

    public Creator create(StorageAccountConfig config) {
        return new Creator(config);
    }

    public class Creator implements ICommittable<StorageAccount>, AzureOperationEvent.Source<StorageAccountConfig> {

        private StorageAccountConfig config;

        Creator(StorageAccountConfig config) {
            this.config = config;
        }

        @Override
        @AzureOperation(name = "storage|account.create", params = {"this.config.getName()"}, type = AzureOperation.Type.SERVICE)
        public StorageAccount commit() {
            com.azure.resourcemanager.storage.models.StorageAccount.DefinitionStages.WithCreate withCreate = StorageManagerFactory
                    .create(config.getSubscriptionId()).storageAccounts()
                    .define(config.getName())
                    .withRegion(config.getRegion().getName())
                    .withExistingResourceGroup(config.getResourceGroupName())
                    .withSku(StorageAccountSkuType.fromSkuName(SkuName.fromString(config.getReplication())));
            if (Kind.STORAGE.toString().equals(config.getKind())) {
                withCreate = withCreate.withGeneralPurposeAccountKind();
            } else if (Kind.STORAGE_V2.toString().equals(config.getKind())) {
                withCreate = withCreate.withGeneralPurposeAccountKindV2();
            } else if (Kind.BLOB_STORAGE.toString().equals(config.getKind())) {
                withCreate = withCreate.withBlobStorageAccountKind();
            } else if (Kind.FILE_STORAGE.toString().equals(config.getKind())) {
                withCreate = withCreate.withFileStorageAccountKind();
            } else {
                withCreate = withCreate.withBlockBlobStorageAccountKind();
            }
            com.azure.resourcemanager.storage.models.StorageAccount account = withCreate.create();
            return new StorageAccount(account.manager(), account);
        }

        public AzureOperationEvent.Source<StorageAccountConfig> getEventSource() {
            return new AzureOperationEvent.Source<StorageAccountConfig>() {};
        }
    }

}
