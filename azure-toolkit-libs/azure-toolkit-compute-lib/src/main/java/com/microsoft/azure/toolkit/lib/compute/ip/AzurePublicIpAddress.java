/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.compute.ip;

import com.azure.resourcemanager.network.models.PublicIpAddresses;
import com.microsoft.azure.toolkit.lib.common.model.Subscription;
import com.microsoft.azure.toolkit.lib.compute.AbstractAzureResourceModule;
import com.microsoft.azure.toolkit.lib.compute.ComputeManagerFactory;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Collectors;

public class AzurePublicIpAddress extends AbstractAzureResourceModule<PublicIpAddress> {

    public AzurePublicIpAddress() {
        super(AzurePublicIpAddress::new);
    }

    private AzurePublicIpAddress(@Nonnull final List<Subscription> subscriptions) {
        super(AzurePublicIpAddress::new, subscriptions);
    }

    @Override
    public List<PublicIpAddress> list(@Nonnull String subscriptionId) {
        return getPublicIpAddressManager(subscriptionId).list().stream().map(ip -> new PublicIpAddress(ip, this)).collect(Collectors.toList());
    }

    @Nonnull
    @Override
    public PublicIpAddress get(@Nonnull final String subscriptionId, @Nonnull final String resourceGroup, @Nonnull final String name) {
        final PublicIpAddresses virtualMachinesManager = getPublicIpAddressManager(subscriptionId);
        return new PublicIpAddress(virtualMachinesManager.getByResourceGroup(resourceGroup, name), this);
    }

    public PublicIpAddress create(@Nonnull final DraftPublicIpAddress draftPublicIpAddress) {
        return draftPublicIpAddress.create(this);
    }

    public PublicIpAddresses getPublicIpAddressManager(String subscriptionId) {
        return ComputeManagerFactory.create(subscriptionId).networkManager().publicIpAddresses();
    }

    @Override
    public String name() {
        return "PublicIpAddress";
    }
}
