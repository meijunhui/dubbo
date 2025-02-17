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

import org.apache.dubbo.common.config.Environment;
import org.apache.dubbo.common.context.ApplicationExt;
import org.apache.dubbo.common.deploy.ApplicationDeployer;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.extension.ExtensionScope;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.manager.ExecutorRepository;
import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.context.ConfigManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link ExtensionLoader}, {@code DubboBootstrap} and this class are at present designed to be
 * singleton or static (by itself totally static or uses some static fields). So the instances
 * returned from them are of process scope. If you want to support multiple dubbo servers in one
 * single process, you may need to refactor those three classes.
 * <p>
 * Represent a application which is using Dubbo and store basic metadata info for using
 * during the processing of RPC invoking.
 * <p>
 * ApplicationModel includes many ProviderModel which is about published services
 * and many Consumer Model which is about subscribed services.
 * <p>
 */

public class ApplicationModel extends ScopeModel {
    protected static final Logger LOGGER = LoggerFactory.getLogger(ApplicationModel.class);
    public static final String NAME = "ApplicationModel";
    private static volatile ApplicationModel defaultInstance;

    private final List<ModuleModel> moduleModels = Collections.synchronizedList(new ArrayList<>());
    private final List<ModuleModel> pubModuleModels = Collections.synchronizedList(new ArrayList<>());
    private Environment environment;
    private ConfigManager configManager;
    private ServiceRepository serviceRepository;

    private final FrameworkModel frameworkModel;

    private ModuleModel internalModule;

    private volatile ModuleModel defaultModule;

    // internal module index is 0, default module index is 1
    private AtomicInteger moduleIndex = new AtomicInteger(0);


    // --------- static methods ----------//

    public static ApplicationModel ofNullable(ApplicationModel applicationModel) {
        if (applicationModel != null) {
            return applicationModel;
        } else {
            return defaultModel();
        }
    }

    public static ApplicationModel defaultModel() {
        if (defaultInstance == null) {
            synchronized (ApplicationModel.class) {
                if (defaultInstance == null) {
                    defaultInstance = new ApplicationModel(FrameworkModel.defaultModel());
                }
            }
        }
        return defaultInstance;
    }

    @Deprecated
    public static Collection<ConsumerModel> allConsumerModels() {
        return defaultModel().getApplicationServiceRepository().allConsumerModels();
    }

    @Deprecated
    public static Collection<ProviderModel> allProviderModels() {
        return defaultModel().getApplicationServiceRepository().allProviderModels();
    }

    @Deprecated
    public static ProviderModel getProviderModel(String serviceKey) {
        return defaultModel().getDefaultModule().getServiceRepository().lookupExportedService(serviceKey);
    }

    @Deprecated
    public static ConsumerModel getConsumerModel(String serviceKey) {
        return defaultModel().getDefaultModule().getServiceRepository().lookupReferredService(serviceKey);
    }

    @Deprecated
    public static Environment getEnvironment() {
        return defaultModel().getModelEnvironment();
    }

    @Deprecated
    public static ConfigManager getConfigManager() {
        return defaultModel().getApplicationConfigManager();
    }

    @Deprecated
    public static ServiceRepository getServiceRepository() {
        return defaultModel().getApplicationServiceRepository();
    }

    @Deprecated
    public static ExecutorRepository getExecutorRepository() {
        return defaultModel().getApplicationExecutorRepository();
    }

    @Deprecated
    public static ApplicationConfig getApplicationConfig() {
        return defaultModel().getCurrentConfig();
    }

    @Deprecated
    public static String getName() {
        return defaultModel().getCurrentConfig().getName();
    }

    @Deprecated
    public static String getApplication() {
        return getName();
    }

    // only for unit test
    @Deprecated
    public static void reset() {
        if (defaultInstance != null) {
            defaultInstance.destroy();
            defaultInstance = null;
        }
    }

    // ------------- instance methods ---------------//

    public ApplicationModel(FrameworkModel frameworkModel) {
        super(frameworkModel, ExtensionScope.APPLICATION);
        Assert.notNull(frameworkModel, "FrameworkModel can not be null");
        this.frameworkModel = frameworkModel;
        frameworkModel.addApplication(this);
        initialize();
    }

    @Override
    protected void initialize() {
        super.initialize();
        internalModule = new ModuleModel(this, true);
        this.serviceRepository = new ServiceRepository(this);

        ExtensionLoader<ApplicationInitListener> extensionLoader = this.getExtensionLoader(ApplicationInitListener.class);
        Set<String> listenerNames = extensionLoader.getSupportedExtensions();
        for (String listenerName : listenerNames) {
            extensionLoader.getExtension(listenerName).init();
        }

        initApplicationExts();

        ExtensionLoader<ScopeModelInitializer> initializerExtensionLoader = this.getExtensionLoader(ScopeModelInitializer.class);
        Set<ScopeModelInitializer> initializers = initializerExtensionLoader.getSupportedExtensionInstances();
        for (ScopeModelInitializer initializer : initializers) {
            initializer.initializeApplicationModel(this);
        }
    }

    private void initApplicationExts() {
        Set<ApplicationExt> exts = this.getExtensionLoader(ApplicationExt.class).getSupportedExtensionInstances();
        for (ApplicationExt ext : exts) {
            ext.initialize();
        }
    }

    @Override
    public void onDestroy() {
        // TODO destroy application resources
        for (ModuleModel moduleModel : new ArrayList<>(moduleModels)) {
            moduleModel.destroy();
        }
        if (defaultInstance == this) {
            synchronized (ApplicationModel.class) {
                frameworkModel.removeApplication(this);
                defaultInstance = null;
            }
        } else {
            frameworkModel.removeApplication(this);
        }

        notifyDestroy();

        if (environment != null) {
            environment.destroy();
            environment = null;
        }
        if (configManager != null) {
            configManager.destroy();
            configManager = null;
        }
        if (serviceRepository != null) {
            serviceRepository.destroy();
            serviceRepository = null;
        }
    }

    public FrameworkModel getFrameworkModel() {
        return frameworkModel;
    }
    public synchronized ModuleModel newModule() {
        return new ModuleModel(this);
    }

    @Override
    public Environment getModelEnvironment() {
        if (environment == null) {
            environment = (Environment) this.getExtensionLoader(ApplicationExt.class)
                .getExtension(Environment.NAME);
        }
        return environment;
    }

    public ConfigManager getApplicationConfigManager() {
        if (configManager == null) {
            configManager = (ConfigManager) this.getExtensionLoader(ApplicationExt.class)
                .getExtension(ConfigManager.NAME);
        }
        return configManager;
    }

    public ServiceRepository getApplicationServiceRepository() {
        return serviceRepository;
    }

    public ExecutorRepository getApplicationExecutorRepository() {
        return this.getExtensionLoader(ExecutorRepository.class).getDefaultExtension();
    }

    public ApplicationConfig getCurrentConfig() {
        return getApplicationConfigManager().getApplicationOrElseThrow();
    }

    public String getApplicationName() {
        return getCurrentConfig().getName();
    }

    public synchronized void addModule(ModuleModel moduleModel, boolean isInternal) {
        if (!this.moduleModels.contains(moduleModel)) {
            this.moduleModels.add(moduleModel);
            moduleModel.setInternalName(buildInternalName(ModuleModel.NAME, getInternalId(), moduleIndex.getAndIncrement()));
            if (!isInternal) {
                pubModuleModels.add(moduleModel);
            }
        }
    }

    public synchronized void removeModule(ModuleModel moduleModel) {
        this.moduleModels.remove(moduleModel);
        this.pubModuleModels.remove(moduleModel);
        if (moduleModel == defaultModule) {
            defaultModule = findDefaultModule();
        }
        if (this.moduleModels.size() == 1 && this.moduleModels.get(0) == internalModule) {
            this.internalModule.destroy();
        }
        if (this.moduleModels.isEmpty()) {
            destroy();
        }
    }

    public List<ModuleModel> getModuleModels() {
        return Collections.unmodifiableList(moduleModels);
    }

    public List<ModuleModel> getPubModuleModels() {
        return pubModuleModels;
    }

    public synchronized ModuleModel getDefaultModule() {
        if (defaultModule == null) {
            defaultModule = findDefaultModule();
            if (defaultModule == null) {
                defaultModule = this.newModule();
            }
        }
        return defaultModule;
    }

    private ModuleModel findDefaultModule() {
        for (ModuleModel moduleModel : moduleModels) {
            if (moduleModel != internalModule) {
                return moduleModel;
            }
        }
        return null;
    }

    public ModuleModel getInternalModule() {
        return internalModule;
    }

    @Deprecated
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Deprecated
    public void setConfigManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Deprecated
    public void setServiceRepository(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @Override
    public void addClassLoader(ClassLoader classLoader) {
        super.addClassLoader(classLoader);
        if (environment != null) {
            environment.refreshClassLoaders();
        }
    }

    @Override
    public void removeClassLoader(ClassLoader classLoader) {
        super.removeClassLoader(classLoader);
        if (environment != null) {
            environment.refreshClassLoaders();
        }
    }

    @Override
    protected boolean checkIfClassLoaderCanRemoved(ClassLoader classLoader) {
        return super.checkIfClassLoaderCanRemoved(classLoader) && !containsClassLoader(classLoader);
    }

    protected boolean containsClassLoader(ClassLoader classLoader) {
        return moduleModels.stream().anyMatch(moduleModel -> moduleModel.getClassLoaders().contains(classLoader));
    }

    public ApplicationDeployer getDeployer() {
        return getAttribute(ModelConstants.DEPLOYER, ApplicationDeployer.class);
    }

    public void setDeployer(ApplicationDeployer deployer) {
        setAttribute(ModelConstants.DEPLOYER, deployer);
    }
}
