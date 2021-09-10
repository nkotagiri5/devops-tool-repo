/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.compute.vm;

import com.azure.resourcemanager.compute.models.AvailabilitySet;
import com.azure.resourcemanager.compute.models.VirtualMachine.DefinitionStages.WithCreate;
import com.azure.resourcemanager.compute.models.VirtualMachine.DefinitionStages.WithLinuxCreateManagedOrUnmanaged;
import com.azure.resourcemanager.compute.models.VirtualMachine.DefinitionStages.WithLinuxRootPasswordOrPublicKeyManagedOrUnmanaged;
import com.azure.resourcemanager.compute.models.VirtualMachine.DefinitionStages.WithProximityPlacementGroup;
import com.microsoft.azure.toolkit.lib.common.model.Region;
import com.microsoft.azure.toolkit.lib.compute.AzureResourceDraft;
import com.microsoft.azure.toolkit.lib.compute.ip.PublicIpAddress;
import com.microsoft.azure.toolkit.lib.compute.network.Network;
import com.microsoft.azure.toolkit.lib.compute.network.model.Subnet;
import com.microsoft.azure.toolkit.lib.compute.security.NetworkSecurityGroup;
import com.microsoft.azure.toolkit.lib.compute.vm.model.AuthenticationType;
import com.microsoft.azure.toolkit.lib.compute.vm.model.OperatingSystem;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.Optional;

@Getter
@Setter
public class DraftVirtualMachine extends VirtualMachine implements AzureResourceDraft<VirtualMachine> {
    private String subscriptionId;
    private String resourceGroup;
    private String name;
    private Region region;
    private AzureImage image;
    private Network network;
    private Subnet subnet;
    private PublicIpAddress ipAddress;
    private NetworkSecurityGroup securityGroup;
    private String userName;
    private AuthenticationType authenticationType;
    private String password;
    private String sshKey;
    private AzureVirtualMachineSize size;
    private String availabilitySet;

    public DraftVirtualMachine(@Nonnull final String subscriptionId, @Nonnull final String resourceGroup, @Nonnull final String name) {
        super(getResourceId(subscriptionId, resourceGroup, name), null);
    }

    @Override
    protected String loadStatus() {
        return Optional.ofNullable(module).map(ignore -> super.loadStatus()).orElse(Status.DRAFT);
    }

    @Nullable
    @Override
    protected com.azure.resourcemanager.compute.models.VirtualMachine loadRemote() {
        return Optional.ofNullable(module).map(ignore -> super.loadRemote()).orElse(null);
    }

    VirtualMachine create(final AzureVirtualMachine module) {
        this.module = module;
        final WithProximityPlacementGroup withProximityPlacementGroup = module.getVirtualMachinesManager(subscriptionId).define(this.getName())
                .withRegion(this.getRegion().getName())
                .withExistingResourceGroup(this.getResourceGroup())
                .withExistingPrimaryNetwork(this.getNetworkClient())
                .withSubnet(subnet.getName())
                .withPrimaryPrivateIPAddressDynamic()
                .withExistingPrimaryPublicIPAddress(getPublicIpAddressClient());
        this.remote = configureImage(withProximityPlacementGroup)
                .withExistingAvailabilitySet(getAvailabilitySetClient())
                .create();
        this.remote.getPrimaryNetworkInterface().update().withExistingNetworkSecurityGroup(getSecurityGroupClient()).apply();
        refreshStatus();
        return this;
    }

    private WithCreate configureImage(final WithProximityPlacementGroup withCreate) {
        if (getImage().getOperatingSystem() == OperatingSystem.Windows) {
            return withCreate.withSpecificWindowsImageVersion(image.getImageReference())
                    .withAdminUsername(userName).withAdminPassword(password).withSize(size.getName());
        } else {
            final WithLinuxRootPasswordOrPublicKeyManagedOrUnmanaged withLinuxImage =
                    withCreate.withSpecificLinuxImageVersion(image.getImageReference()).withRootUsername(userName);
            final WithLinuxCreateManagedOrUnmanaged withLinuxAuthentication = authenticationType == AuthenticationType.Password ?
                    withLinuxImage.withRootPassword(password) : withLinuxImage.withSsh(sshKey);
            return withLinuxAuthentication.withSize(size.getName());
        }
    }

    private com.azure.resourcemanager.network.models.NetworkSecurityGroup getSecurityGroupClient() {
        return Optional.ofNullable(module).map(parent -> parent.getVirtualMachinesManager(subscriptionId).manager())
                .map(manager -> manager.networkManager().networkSecurityGroups().getByResourceGroup(resourceGroup, securityGroup.name())).orElse(null);
    }

    private AvailabilitySet getAvailabilitySetClient() {
        return Optional.ofNullable(module).map(parent -> parent.getVirtualMachinesManager(subscriptionId).manager())
                .map(manager -> manager.availabilitySets().getByResourceGroup(resourceGroup, availabilitySet)).orElse(null);
    }

    private com.azure.resourcemanager.network.models.PublicIpAddress getPublicIpAddressClient() {
        return Optional.ofNullable(module).map(parent -> parent.getVirtualMachinesManager(subscriptionId).manager())
                .map(manager -> manager.networkManager().publicIpAddresses().getById(ipAddress.getId())).orElse(null);
    }

    private com.azure.resourcemanager.network.models.Network getNetworkClient() {
        return Optional.ofNullable(module).map(parent -> parent.getVirtualMachinesManager(subscriptionId).manager())
                .map(manager -> manager.networkManager().networks().getById(network.getId())).orElse(null);
    }

    private static String getResourceId(@Nonnull final String subscriptionId, @Nonnull final String resourceGroup, @Nonnull final String name) {
        return String.format("/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Compute/virtualMachines/%s", subscriptionId, resourceGroup, name);
    }
}