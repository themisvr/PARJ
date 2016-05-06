package madgik.exareme.master.client.rmi;

import madgik.exareme.common.app.engine.*;
import madgik.exareme.common.art.ConcreteOperatorStatistics;
import madgik.exareme.common.art.ContainerSessionStatistics;
import madgik.exareme.common.schema.Index;
import madgik.exareme.common.schema.Partition;
import madgik.exareme.common.schema.PhysicalTable;
import madgik.exareme.common.schema.Table;
import madgik.exareme.master.client.AdpDBClientProperties;
import madgik.exareme.master.client.AdpDBClientQueryStatus;
import madgik.exareme.master.engine.AdpDBQueryExecutionPlan;
import madgik.exareme.master.engine.intermediateCache.Cache;
import madgik.exareme.master.engine.parser.SemanticException;
import madgik.exareme.master.queryProcessor.decomposer.query.SQLQuery;
import madgik.exareme.master.queryProcessor.decomposer.query.SQLQueryParser;
import madgik.exareme.master.registry.Registry;
import madgik.exareme.utils.chart.TimeFormat;
import madgik.exareme.utils.chart.TimeUnit;
import madgik.exareme.utils.embedded.db.TableInfo;
import madgik.exareme.utils.properties.AdpDBProperties;
import org.apache.log4j.Logger;

import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author alex
 */
public class RmiAdpDBClientQueryStatus implements AdpDBClientQueryStatus {
    private final static Logger log = Logger.getLogger(AdpDBClientQueryStatus.class);

    private AdpDBClientProperties properties;
    private AdpDBQueryExecutionPlan plan;
    private AdpDBStatus status;
    private String lastStatus;
    private String resultTableName;
    private TimeFormat timeF;
    private boolean finished;

    public RmiAdpDBClientQueryStatus(AdpDBQueryID queryId, AdpDBClientProperties properties,
                                     AdpDBQueryExecutionPlan plan, AdpDBStatus status) {
        this.properties = properties;
        this.plan = plan;
        this.status = status;
        this.resultTableName = null;
        this.lastStatus = null;
        this.timeF = new TimeFormat(TimeUnit.min);
        this.finished = false;
    }

    @Override
    public boolean hasFinished() throws RemoteException {
        if (finished) {
            return true;
        }
        if (status.hasFinished() == false && status.hasError() == false) {
            return false;
        }
        updateRegistry();
        finished = true;
        return true;
    }

    @Override
    public AdpDBQueryID getQueryID() {
        return plan.getQueryID();
    }

    @Override
    public String getStatus() throws RemoteException {
        lastStatus = status.getStatistics().toString();
        return lastStatus;
    }

    @Override
    public String getStatusIfChanged() throws RemoteException {
        String currentStatus = status.getStatistics().toString();
        if (!currentStatus.equals(lastStatus)) {
            lastStatus = currentStatus;
            return lastStatus;
        }
        return null;
    }

    @Override
    public boolean hasError() throws RemoteException {
        if (!finished)
            return false;
        return status.hasError();
    }

    @Override
    public String getError() throws RemoteException {
        return status.getLastException().toString();
    }

    @Override
    public String getExecutionTime() throws RemoteException {
        long startTime = status.getStatistics().getAdpEngineStatistics().startTime;
        long endTime = status.getStatistics().getAdpEngineStatistics().endTime;
        return String.format("%s m", timeF.format(endTime - startTime));
    }

    @Override
    public void close() throws RemoteException {
        status.stopExecution();
    }

    @Override
    public void registerListener(AdpDBQueryListener listener) throws RemoteException {
        status.registerListener(listener);
    }

    private void updateRegistry() throws RemoteException {
        if (status.hasError() == false) {

            // update registry
            HashMap<String, List<TableInfo>> resultTables = new HashMap<>();
            HashMap<String, String> sqlQueries = new HashMap<>();
            HashMap<String, List<ExecuteQueryExitMessage>> exitMessageMap = new HashMap<>();
            List<ExecuteQueryExitMessage> queryExitMessageList;

            List<TableInfo> tables;
            Set<String> tableSet = new HashSet<>();

            // containers
            for (ContainerSessionStatistics containerStat : status.getStatistics()
                    .getAdpEngineStatistics().containerStats) {
                // operators
                for (ConcreteOperatorStatistics operatorStatistics : containerStat.operators) {
                    ExecuteQueryExitMessage exitMessage =
                            (ExecuteQueryExitMessage) operatorStatistics.getExitMessage();
                    // get
                    if (exitMessage != null) {

                        if (exitMessage.type == AdpDBOperatorType.tableUnionReplicator) {

                            tables = resultTables.get(exitMessage.outTableInfo.getTableName());
                            queryExitMessageList = exitMessageMap.get(exitMessage.outTableInfo.getTableName());
                            tableSet.add(exitMessage.outTableInfo.getTableName());

                            if (tables == null) {
                                tables = new LinkedList<TableInfo>();
                                resultTables.put(exitMessage.outTableInfo.getTableName(), tables);
                                queryExitMessageList = new LinkedList<ExecuteQueryExitMessage>();
                                exitMessageMap.put(exitMessage.outTableInfo.getTableName(), queryExitMessageList);
                            }
                            tables.add(exitMessage.outTableInfo);
                            queryExitMessageList.add(exitMessage);

                        } else if (exitMessage.type == AdpDBOperatorType.runQuery) {
                            sqlQueries.put(exitMessage.outTableInfo.getTableName(), exitMessage.outTableInfo.getSqlQuery());
                        }
                    }
                }
            }

            //      log.debug(
            //          ConsoleFactory.getDefaultStatisticsFormat().format(
            //              status.getStatistics().getAdpEngineStatistics(),
            //              plan.getGraph(),
            //              RmiAdpDBSelectScheduler.runTimeParams
            //          )
            //      );

            // Adding result tables, indexes to schema.

            Registry registry = Registry.getInstance(properties.getDatabase());

            SQLQuery sqlQuery;
            Set<String> usedCachedTables = new HashSet<>();
            int totalSize;
            Set<String> pernamentTables = new HashSet<>();
            Cache cache = new Cache(properties);
            //loop sta pernament tables
            for (PhysicalTable resultTable : plan.getResultTables()) {

                //                for (Select selectQuery : plan.getScript().getSelectQueries()) {
//                    if (selectQuery.getParsedSqlQuery().getResultTable().equals(resultTable.getName())) {
//                        for (String column : selectQuery.getParsedSqlQuery().getPartitionColumns()) {
//                            resultTable.addPartitionColumn(column);
//                        }
//                        break;
//                    }
//                }
                pernamentTables.add(resultTable.getTable().getName());
                //TableInfo tableInfo = resultTables.get(resultTable.getName()).get(0);
                totalSize = 0;
                queryExitMessageList = exitMessageMap.get(resultTable.getName());
                for (int part = 0; part < resultTable.getPartitions().size(); ++part) {
//          System.out.println("~pnum " + resultTable.getPartitions().get(part).getpNum());
                    for (ExecuteQueryExitMessage message : queryExitMessageList) {
                        if (message.serialNumber == resultTable.getPartitions().get(part).getpNum()) {

                            resultTable.getPartition(part).setSize(message.outTableInfo.getSizeInBytes());
                            totalSize += message.outTableInfo.getSizeInBytes();
                            break;
                        }
                    }
                }
                resultTable.getTable().setSqlQuery(sqlQueries.get(resultTable.getName()));
                resultTable.getTable().setSize(totalSize);

                if (resultTable.getTable().hasSQLDefinition() == false) {
                    if (resultTables.containsKey(resultTable.getName()) == false) {
                        throw new SemanticException(
                                "Table definition not found: " + resultTable.getName());
                    }
                    resultTableName = resultTable.getName();
                    String sqlDef = resultTables.get(resultTable.getName()).get(0).getSQLDefinition();
                    resultTable.getTable().setSqlDefinition(sqlDef);
                }
                resultTable.getTable().setTemp(false);
                registry.addPhysicalTable(resultTable);
                cache.unpinTable(resultTable.getName());    //paizei na mn exei noima edw mias kai anaferete se pernament table. Sto shmeio auto prepei apla na mpei gia ta from tables


                try {
                    sqlQuery = SQLQueryParser.parse(resultTable.getTable().getSqlQuery().replaceAll("_", " ").replaceAll("\\ {2,}", "_"), new madgik.exareme.master.queryProcessor.decomposer.dag.NodeHashValues());

//                    List<Table> usedTables = new ArrayList<>(sqlQuery.getInputTables().size());
                    for (madgik.exareme.master.queryProcessor.decomposer.query.Table usedTable : sqlQuery.getInputTables()) {
                        usedCachedTables.add(usedTable.getName());
                    }
//                    cache.updateCacheForTableUse(usedTables);
                } catch (Exception e) {
                }
            }


            DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            java.util.Date dateobj = new Date();
            if (AdpDBProperties.getAdpDBProps().getString("db.cache").equals("true")) {

                cache = new Cache(properties, Integer.parseInt(AdpDBProperties.getAdpDBProps().getString("db.cacheSize")));

                Table table;
                Partition partition;
                PhysicalTable resultTable;
                HashMap<String, Integer> map;
                // Adding temporary tables to schema
                for (String tableName : tableSet) {

                    if (!pernamentTables.contains(tableName)) {  //Temporary table

                        table = new Table(tableName);
                        table.setTemp(true);
                        table.setHashID(plan.getScript().getTable(tableName).getTable().getHashID());
                        table.setNumOfAccess(0);
                        table.setLastAccess(df.format(dateobj));

                        resultTable = new PhysicalTable(table);
                        for (String column : plan.getScript().getTable(tableName).getPartitionColumns()) {
                            resultTable.addPartitionColumn(column);
                        }

                        //edwww
//                        for (Select selectQuery : plan.getScript().getSelectQueries()) {
//                            if (selectQuery.getParsedSqlQuery().getResultTable().equals(tableName)) {
//                                for (String column : selectQuery.getParsedSqlQuery().getPartitionColumns()) {
//                                    resultTable.addPartitionColumn(column);
//                                }
//                                break;
//                            }
//                        }

                        TableInfo tableInfo = resultTables.get(tableName).get(0);
                        totalSize = 0;
                        int size;
                        queryExitMessageList = exitMessageMap.get(tableName);
                        map = new HashMap<>();
                        for (int part = 0; part < queryExitMessageList.size(); ++part) { //resultTable.getPartitions().size()
                            for (ExecuteQueryExitMessage message : queryExitMessageList) {
                                if (message.serialNumber == part) {  //resultTable.getPartitions().get(part).getpNum()

                                    partition = new Partition(tableName, part);
                                    partition.setSize(message.outTableInfo.getSizeInBytes());
                                    partition.addLocation(message.outTableInfo.getLocation());
                                    resultTable.addPartition(partition);
                                    size = message.outTableInfo.getSizeInBytes();
                                    totalSize += size;
                                    if (!map.containsKey(message.outTableInfo.getLocation())) {
                                        map.put(message.outTableInfo.getLocation(), size);
                                    } else {
                                        size += map.get(message.outTableInfo.getLocation());
                                        map.put(message.outTableInfo.getLocation(), size);
                                    }
                                    break;
                                }
                            }
                        }
                        resultTable.getTable().setSqlQuery(sqlQueries.get(queryExitMessageList.get(0).outTableInfo.getTableName()));
                        resultTable.getTable().setSize(totalSize);
                        table.setSize(totalSize);
                        try {
//                            System.out.println("map " + map);
                            cache.updateCache(table, map);
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }

                        if (resultTable.getTable().hasSQLDefinition() == false) {
                            if (resultTables.containsKey(resultTable.getName()) == false) {
                                throw new SemanticException(
                                        "Table definition not found: " + resultTable.getName());
                            }
                            resultTable.getTable().setSqlDefinition(tableInfo.getSQLDefinition());
                        }
                        registry.addPhysicalTable(resultTable);

                        try {
                            sqlQuery = SQLQueryParser.parse(resultTable.getTable().getSqlQuery().replaceAll("_", " ").replaceAll("\\ {2,}", "_"), new madgik.exareme.master.queryProcessor.decomposer.dag.NodeHashValues());

//                            List<Table> usedTables = new ArrayList<>(sqlQuery.getInputTables().size());
                            for (madgik.exareme.master.queryProcessor.decomposer.query.Table usedTable : sqlQuery.getInputTables()) {
                                usedCachedTables.add(usedTable.getName());
                            }
//                            cache.updateCacheForTableUse(usedTables);
                        } catch (Exception e) {
                        }

                    }
                }
            }

            System.out.println("stagee1");
            for(String name : tableSet){
                System.out.println("uparxei sto set to "+name);
            }

            System.out.println("Stage22");
            for(String name : pernamentTables){
                System.out.println("uparxei sto set to "+name);
            }
            System.out.println("telos stage");

            List<Table> usedTables = new ArrayList<>(usedCachedTables.size());
            for(String cachedTableName : usedCachedTables){
                if(!tableSet.contains(cachedTableName) && !pernamentTables.contains(cachedTableName)) {
                    System.out.println("tha kanw update to "+cachedTableName);
                    usedTables.add(new Table(cachedTableName));
                }
            }
            cache.updateCacheForTableUse(usedTables);

            for (Index index : plan.getBuildIndexes()) {
                registry.addIndex(index);
            }

            //Drop tables
            for (AdpDBDMOperator dmOP : plan.getDataManipulationOperators()) {
                if (dmOP.getType().equals(AdpDBOperatorType.dropTable)) {
                    registry.removePhysicalTable(dmOP.getDMQuery().getTable());
                }
            }
            log.debug("Registry updated.");
        }
    }
}
