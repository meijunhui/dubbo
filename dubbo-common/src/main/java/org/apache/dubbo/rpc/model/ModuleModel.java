/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.rpc.model;

import org.apache.dubbo.common.config.ModuleEnvironment;
import org.apache.dubbo.common.context.ModuleExt;
import org.apache.dubbo.common.deploy.ModuleDeployer;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.extension.ExtensionScope;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.config.context.ModuleConfigManager;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Model of a service module
 */
public class ModuleModel extends ScopeModel {
    private static final Logger logger = LoggerFactory.getLogger(ModuleModel.class);

    private static final AtomicLong index = new AtomicLong(0);
    public static final String NAME = "ModuleModel";

    private final ApplicationModel applicationModel;
    private ModuleEnvironment moduleEnvironment;
    private ModuleServiceRepository serviceRepository;
    private ModuleConfigManager moduleConfigManager;

    public ModuleModel(ApplicationModel applicationModel) {
        this(applicationModel, false);
    }

    public ModuleModel(ApplicationModel applicationModel, boolean isInternal) {
        super(applicationModel, ExtensionScope.MODULE);
        Assert.notNull(applicationModel, "ApplicationModel can not be null");
        this.applicationModel = applicationModel;
        applicationModel.addModule(this, isInternal);
        initialize();
        Assert.notNull(applicationModel, "ApplicationModel can not be null");
    }

    @Override
    protected void initialize() {
        super.initialize();
        this.serviceRepository = new ModuleServiceRepository(this);
        this.moduleConfigManager = new ModuleConfigManager(this);
        this.moduleConfigManager.initialize();

        initModuleExt();

        ExtensionLoader<ScopeModelInitializer> initializerExtensionLoader = this.getExtensionLoader(ScopeModelInitializer.class);
        Set<ScopeModelInitializer> initializers = initializerExtensionLoader.getSupportedExtensionInstances();
        for (ScopeModelInitializer initializer : initializers) {
            initializer.initializeModuleModel(this);
        }
    }

    private void initModuleExt() {
        Set<ModuleExt> exts = this.getExtensionLoader(ModuleExt.class).getSupportedExtensionInstances();
        for (ModuleExt ext : exts) {
            ext.initialize();
        }
    }

    @Override
    public void onDestroy() {
        if (serviceRepository != null) {
            List<ConsumerModel> consumerModels = serviceRepository.getReferredServices();

            for (ConsumerModel consumerModel : consumerModels) {
                try {
                    if (consumerModel.getReferenceConfig() != null) {
                        consumerModel.getReferenceConfig().destroy();
                    } else if (consumerModel.getDestroyCaller() != null) {
                        consumerModel.getDestroyCaller().call();
                    }
                } catch (Throwable t) {
                    logger.error("Unable to destroy consumerModel.", t);
                }
            }

            List<ProviderModel> exportedServices = serviceRepository.getExportedServices();
            for (ProviderModel providerModel : exportedServices) {
                try {
                    if (providerModel.getServiceConfig() != null) {
                        providerModel.getServiceConfig().unexport();
                    } else if (providerModel.getDestroyCaller() != null) {
                        providerModel.getDestroyCaller().call();
                    }
                } catch (Throwable t) {
                    logger.error("Unable to destroy providerModel.", t);
                }
            }

            serviceRepository.destroy();
            serviceRepository = null;
        }

        notifyDestroy();
        if (moduleEnvironment != null) {
            moduleEnvironment.destroy();
            moduleEnvironment = null;
        }

        applicationModel.removeModule(this);
    }

    public ApplicationModel getApplicationModel() {
        return applicationModel;
    }

    public ModuleServiceRepository getServiceRepository() {
        return serviceRepository;
    }

    @Override
    public ModuleEnvironment getModelEnvironment() {
        if (moduleEnvironment == null) {
            moduleEnvironment = (ModuleEnvironment) this.getExtensionLoader(ModuleExt.class)
                .getExtension(ModuleEnvironment.NAME);
        }
        return moduleEnvironment;
    }

    public ModuleConfigManager getConfigManager() {
        return moduleConfigManager;
    }

    public ModuleDeployer getDeployer() {
        return getAttribute(ModelConstants.DEPLOYER, ModuleDeployer.class);
    }

    public void setDeployer(ModuleDeployer deployer) {
        setAttribute(ModelConstants.DEPLOYER, deployer);
    }

}
