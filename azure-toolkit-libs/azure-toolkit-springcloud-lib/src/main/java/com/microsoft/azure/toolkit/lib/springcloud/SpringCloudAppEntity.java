/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.springcloud;

import com.azure.resourcemanager.appplatform.models.PersistentDisk;
import com.azure.resourcemanager.appplatform.models.SpringApp;
import com.azure.resourcemanager.resources.fluentcore.arm.models.ExternalChildResource;
import com.microsoft.azure.toolkit.lib.common.entity.AbstractAzureResource.RemoteAwareResourceEntity;
import com.microsoft.azure.toolkit.lib.springcloud.config.SpringCloudAppConfig;
import com.microsoft.azure.toolkit.lib.springcloud.model.SpringCloudPersistentDisk;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;

@Getter
public class SpringCloudAppEntity extends RemoteAwareResourceEntity<SpringApp> {
    private final SpringCloudClusterEntity cluster;
    private final String name;
    private SpringCloudAppConfig config;

    public SpringCloudAppEntity(@Nonnull final String name, @Nonnull final SpringCloudClusterEntity cluster) {
        this.name = name;
        this.cluster = cluster;
    }

    public SpringCloudAppEntity(@Nonnull final SpringCloudAppConfig config, @Nonnull final SpringCloudClusterEntity cluster) {
        this.config = config;
        this.name = config.getAppName();
        this.cluster = cluster;
    }

    SpringCloudAppEntity(@Nonnull final SpringApp resource, @Nonnull final SpringCloudClusterEntity cluster) {
        this.remote = resource;
        this.name = resource.name();
        this.cluster = cluster;
    }

    public boolean isPublic() {
        if (Objects.nonNull(this.remote)) {
            return this.remote.isPublic();
        }
        if (Objects.nonNull(this.config)) {
            return this.config.isPublic();
        }
        return false;
    }

    @Nullable
    public String getApplicationUrl() {
        final String url = Optional.ofNullable(this.remote).map(SpringApp::url).orElse(null);
        return StringUtils.isBlank(url) || url.equalsIgnoreCase("None") ? null : url;
    }

    @Nullable
    public String getTestUrl() {
        return Optional.ofNullable(this.remote).map(SpringApp::activeDeploymentName).map(d -> {
            final String endpoint = this.remote.parent().listTestKeys().primaryTestEndpoint();
            return String.format("%s/%s/%s/", endpoint, this.name, d);
        }).orElse(null);
    }

    @Nullable
    public String getLogStreamingEndpoint(String instanceName) {
        return Optional.ofNullable(this.remote).map(SpringApp::activeDeploymentName).map(d -> {
            final String endpoint = this.remote.parent().listTestKeys().primaryTestEndpoint();
            return String.format("%s/api/logstream/apps/%s/instances/%s", endpoint.replace(".test", ""), this.name, instanceName);
        }).orElse(null);
    }

    @Override
    public String getId() {
        return Optional.ofNullable(this.remote).map(ExternalChildResource::id)
                .orElse(this.cluster.getId() + "/apps/" + this.name);
    }

    @Nullable
    public SpringCloudPersistentDisk getPersistentDisk() {
        final PersistentDisk disk = Optional.ofNullable(this.remote).map(SpringApp::persistentDisk).orElse(null);
        return Optional.ofNullable(disk).filter(d -> d.sizeInGB() > 0)
                .map(d -> SpringCloudPersistentDisk.builder()
                        .sizeInGB(disk.sizeInGB())
                        .mountPath(disk.mountPath())
                        .usedInGB(disk.usedInGB()).build())
                .orElse(null);
    }

    public SpringCloudDeploymentEntity activeDeployment() {
        return Optional.ofNullable(this.remote).map(SpringApp::getActiveDeployment)
                .map(d -> new SpringCloudDeploymentEntity(d, this))
                .orElse(new SpringCloudDeploymentEntity("default", this));
    }

    @Override
    @Nonnull
    public String getSubscriptionId() {
        return cluster.getSubscriptionId();
    }
}
