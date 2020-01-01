/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Plan is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Plan. If not, see <https://www.gnu.org/licenses/>.
 */
package com.djrapitops.plan.extension.implementation.providers.gathering;

import com.djrapitops.plan.extension.CallEvents;
import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.icon.Icon;
import com.djrapitops.plan.extension.implementation.DataProviderExtractor;
import com.djrapitops.plan.extension.implementation.ProviderInformation;
import com.djrapitops.plan.extension.implementation.TabInformation;
import com.djrapitops.plan.extension.implementation.providers.DataProvider;
import com.djrapitops.plan.extension.implementation.providers.DataProviders;
import com.djrapitops.plan.extension.implementation.providers.MethodWrapper;
import com.djrapitops.plan.extension.implementation.providers.Parameters;
import com.djrapitops.plan.extension.implementation.storage.transactions.StoreIconTransaction;
import com.djrapitops.plan.extension.implementation.storage.transactions.StorePluginTransaction;
import com.djrapitops.plan.extension.implementation.storage.transactions.StoreTabInformationTransaction;
import com.djrapitops.plan.extension.implementation.storage.transactions.providers.StoreProviderTransaction;
import com.djrapitops.plan.extension.implementation.storage.transactions.results.*;
import com.djrapitops.plan.identification.ServerInfo;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.Database;
import com.djrapitops.plan.storage.database.transactions.Transaction;

import java.util.UUID;

/**
 * Object that can be called to place data about players to the database.
 *
 * @author Rsl1122
 */
public class ProviderValueGatherer {

    private final DataExtension extension;

    private final CallEvents[] callEvents;
    private final DataProviderExtractor extractor;
    private final DBSystem dbSystem;
    private final ServerInfo serverInfo;

    private final DataProviders dataProviders;
    private final BooleanProviderValueGatherer booleanGatherer;
    private final TableProviderValueGatherer tableGatherer;
    private final Gatherer<Long> serverNumberGatherer;
    private final Gatherer<Long> playerNumberGatherer;
    private final Gatherer<Double> serverDoubleGatherer;
    private final Gatherer<Double> playerDoubleGatherer;
    private final Gatherer<String> serverStringGatherer;
    private final Gatherer<String> playerStringGatherer;
    private final Gatherer<String[]> playerGroupGatherer;

    public ProviderValueGatherer(
            DataExtension extension,
            DataProviderExtractor extractor,
            DBSystem dbSystem,
            ServerInfo serverInfo
    ) {
        this.extension = extension;
        this.callEvents = this.extension.callExtensionMethodsOn();
        this.extractor = extractor;
        this.dbSystem = dbSystem;
        this.serverInfo = serverInfo;

        String pluginName = extractor.getPluginName();
        UUID serverUUID = serverInfo.getServerUUID();
        Database database = dbSystem.getDatabase();
        dataProviders = extractor.getProviders();
        booleanGatherer = new BooleanProviderValueGatherer(
                pluginName, extension, serverUUID, database, dataProviders
        );
        tableGatherer = new TableProviderValueGatherer(
                pluginName, extension, serverUUID, database, dataProviders
        );

        serverNumberGatherer = new Gatherer<>(
                Long.class, StoreServerNumberResultTransaction::new
        );
        serverDoubleGatherer = new Gatherer<>(
                Double.class, StoreServerDoubleResultTransaction::new
        );
        serverStringGatherer = new Gatherer<>(
                String.class, StoreServerStringResultTransaction::new
        );
        playerNumberGatherer = new Gatherer<>(
                Long.class, StorePlayerNumberResultTransaction::new
        );
        playerDoubleGatherer = new Gatherer<>(
                Double.class, StorePlayerDoubleResultTransaction::new
        );
        playerStringGatherer = new Gatherer<>(
                String.class, StorePlayerStringResultTransaction::new
        );
        playerGroupGatherer = new Gatherer<>(
                String[].class, StorePlayerGroupsResultTransaction::new
        );
    }

    public void disableMethodFromUse(MethodWrapper<?> method) {
        dataProviders.removeProviderWithMethod(method);
    }

    public boolean shouldSkipEvent(CallEvents event) {
        if (event == CallEvents.MANUAL) {
            return false;
        }
        for (CallEvents accepted : callEvents) {
            if (event == accepted) {
                return false;
            }
        }
        return true;
    }

    public String getPluginName() {
        return extractor.getPluginName();
    }

    public void storeExtensionInformation() {
        String pluginName = extractor.getPluginName();
        Icon pluginIcon = extractor.getPluginIcon();

        long time = System.currentTimeMillis();
        UUID serverUUID = serverInfo.getServerUUID();

        Database database = dbSystem.getDatabase();
        database.executeTransaction(new StoreIconTransaction(pluginIcon));
        database.executeTransaction(new StorePluginTransaction(pluginName, time, serverUUID, pluginIcon));
        for (TabInformation tab : extractor.getPluginTabs()) {
            database.executeTransaction(new StoreIconTransaction(tab.getTabIcon()));
            database.executeTransaction(new StoreTabInformationTransaction(pluginName, serverUUID, tab));
        }

        database.executeTransaction(new RemoveInvalidResultsTransaction(pluginName, serverUUID, extractor.getInvalidatedMethods()));
    }

    public void updateValues(UUID playerUUID, String playerName) {
        Conditions conditions = booleanGatherer.gatherBooleanDataOfPlayer(playerUUID, playerName);
        Parameters params = Parameters.player(serverInfo.getServerUUID(), playerUUID, playerName);
        playerNumberGatherer.gather(conditions, params);
        playerDoubleGatherer.gather(conditions, params);
        playerStringGatherer.gather(conditions, params);
        tableGatherer.gatherTableDataOfPlayer(playerUUID, playerName, conditions);
        playerGroupGatherer.gather(conditions, params);
    }

    public void updateValues() {
        Conditions conditions = booleanGatherer.gatherBooleanDataOfServer();
        Parameters params = Parameters.server(serverInfo.getServerUUID());

        serverNumberGatherer.gather(conditions, params);
        serverDoubleGatherer.gather(conditions, params);
        serverStringGatherer.gather(conditions, params);
        tableGatherer.gatherTableDataOfServer(conditions);
    }

    interface ResultTransactionConstructor<T> {
        Transaction create(DataProvider<T> provider, Parameters parameters, T result);
    }

    class Gatherer<T> {
        private final Class<T> type;
        private final ResultTransactionConstructor<T> resultTransactionConstructor;

        public Gatherer(
                Class<T> type,
                ResultTransactionConstructor<T> resultTransactionConstructor
        ) {
            this.type = type;
            this.resultTransactionConstructor = resultTransactionConstructor;
        }

        public void gather(Conditions conditions, Parameters parameters) {
            for (DataProvider<T> provider : dataProviders.getProvidersByTypes(parameters.getMethodType(), type)) {
                gather(conditions, provider, parameters);
            }
        }

        private void gather(Conditions conditions, DataProvider<T> provider, Parameters parameters) {
            ProviderInformation information = provider.getProviderInformation();
            if (information.getCondition().map(conditions::isNotFulfilled).orElse(false)) {
                return; // Condition not fulfilled
            }

            T result = provider.getMethod().callMethod(extension, parameters);
            if (result == null) {
                return; // Error during method call
            }

            Database db = dbSystem.getDatabase();
            db.executeTransaction(new StoreIconTransaction(information.getIcon()));
            db.executeTransaction(new StoreProviderTransaction(provider, parameters.getServerUUID()));
            db.executeTransaction(resultTransactionConstructor.create(provider, parameters, result));
        }
    }
}