/*
 *  Copyright (c) 2023, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.persist.cmd;

import io.ballerina.cli.BLauncherCmd;
import io.ballerina.persist.BalException;
import io.ballerina.persist.PersistToolsConstants;
import io.ballerina.persist.models.Entity;
import io.ballerina.persist.models.EntityField;
import io.ballerina.persist.models.FieldMetadata;
import io.ballerina.persist.models.ForeignKey;
import io.ballerina.persist.models.Module;
import io.ballerina.persist.nodegenerator.SourceGenerator;
import io.ballerina.persist.nodegenerator.syntax.constants.BalSyntaxConstants;
import io.ballerina.persist.nodegenerator.syntax.utils.SqlScriptUtils;
import io.ballerina.persist.nodegenerator.syntax.utils.TomlSyntaxUtils;
import io.ballerina.persist.utils.BalProjectUtils;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This Class implements the `persist migrate` command in Ballerina
 * persist-tool.
 *
 * @since 0.4.0
 */
@CommandLine.Command(name = "migrate", description = "Generate DB migration scripts for Ballerina schema changes.")

public class Migrate implements BLauncherCmd {

    private static final PrintStream errStream = System.err;

    private final String sourcePath;

    private static final String COMMAND_IDENTIFIER = "persist-migrate";

    @CommandLine.Parameters
    public List<String> argList;

    public Migrate() {
        this("");
    }

    public Migrate(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    @CommandLine.Option(names = { "-h", "--help" }, hidden = true)
    private boolean helpFlag;

    @Override
    public void execute() {
        if (helpFlag) {
            String commandUsageInfo = BLauncherCmd.getCommandUsageInfo(COMMAND_IDENTIFIER);
            errStream.println(commandUsageInfo);
            return;
        }

        errStream.printf(BalSyntaxConstants.EXPERIMENTAL_NOTICE, "The support for migrations is currently an " +
                "experimental feature, and its behavior may be subject to change in future releases.");

        Path projectPath = Paths.get(sourcePath).toAbsolutePath();
        try {
            BalProjectUtils.validateBallerinaProject(projectPath);
        } catch (BalException e) {
            errStream.println(e.getMessage());
            return;
        }

        String dataStore = "";

        try {
            HashMap<String, String> ballerinaTomlConfig = TomlSyntaxUtils.readBallerinaTomlConfig(
                    Paths.get(this.sourcePath, "Ballerina.toml"));
            dataStore = ballerinaTomlConfig.get("datastore").trim();
            if (!dataStore.equals(PersistToolsConstants.SupportedDataSources.MYSQL_DB) || 
                    !dataStore.equals(PersistToolsConstants.SupportedDataSources.GOOGLE_SHEETS)) {
                errStream.printf("ERROR: unsupported data store: expected: 'mysql' or " +
                "'googlesheets' but found: '%s'%n", dataStore);
                return;
            }
        } catch (BalException e) {
            errStream.printf("ERROR: failed to locate Ballerina.toml: %s%n",
                    e.getMessage());
            return;
        }

        // Check if the migration name is given
        if (null == argList || argList.isEmpty()) {
            errStream.println("Error: migration label is not provided. Provide the migration label " +
                    "along with the command like `bal persist migrate <migration-label>`");
            return;
        }
        // Check if one argument is given and not more than one argument.
        if (1 < argList.size()) {
            errStream.println("Error: Too many arguments provided. Only one argument " +
                    "is allowed to pass for migration label");
            return;
        }

        String migrationName = argList.get(0);

        // Returns the path of the bal file in the persist directory
        Path schemaFilePath;
        try {
            schemaFilePath = BalProjectUtils.getSchemaFilePath(this.sourcePath);
        } catch (BalException e) {
            errStream.println(e.getMessage());
            return;
        }

        migrate(migrationName, projectPath, this.sourcePath, schemaFilePath, dataStore);

    }

    private static void migrate(String migrationName, Path projectDirPath, String sourcePath, 
        Path schemaFilePath, String dataStore) {
        if (schemaFilePath != null) {
            Path persistDirPath = Paths.get(projectDirPath.toString(), "persist");

            // Create a File object for the persist directory
            File persistDir = new File(persistDirPath.toString());

            // Create a File object for the migrations directory
            File migrationsDir = new File(persistDir, "migrations");

            // Check if the migrations directory exists
            if (!migrationsDir.exists()) {
                // Create the migrations directory
                boolean created = migrationsDir.mkdir();
                if (!created) {
                    errStream.println("Error: failed to create migrations directory inside the persist directory");
                    return;
                }

                Module model = null;
                try {
                    model = BalProjectUtils.getEntities(schemaFilePath);
                } catch (BalException e) {
                    errStream.println("Error getting entities: " + e.getMessage());
                    return;
                }

                String folderName = createTimestampFolder(migrationName);
                String newMigration = migrationsDir + File.separator + folderName;

                createTimestampDirectory(newMigration);

                Path newMigrationPath = Paths.get(newMigration);

                if (!model.getEntityMap().isEmpty()) {
                    try {
                        // Generate the SQL script
                        SourceGenerator.addSqlScriptFile("the migrate command",
                                SqlScriptUtils.generateSqlScript(model.getEntityMap().values(),
                                        PersistToolsConstants.SupportedDataSources.MYSQL_DB), newMigrationPath);
                    } catch (BalException e) {
                        errStream.println("ERROR: failed to generate SQL script " + e.getMessage());
                        return;
                    }

                    try {
                        // Copy the source file to the destination folder
                        Files.copy(schemaFilePath, newMigrationPath.resolve(schemaFilePath.getFileName()));
                    } catch (IOException e) {
                        errStream.println("Error: Copying file failed: " + e.getMessage());
                        return;
                    }

                    //Get the relative path of the migration directory from the project root
                    Path relativePath = Paths.get("").toAbsolutePath().relativize(newMigrationPath);

                    errStream.println(System.lineSeparator() + "Detailed list of differences: ");
                    errStream.println("[Entity " + String.join(", ", model.getEntityMap().keySet())
                            + " has been added]" + System.lineSeparator());
                    errStream.println(
                            "Generated migration script to " + relativePath.toString() + 
                            " directory." + System.lineSeparator());
                    errStream.println("Next steps:" + System.lineSeparator() + 
                            "Execute the \"script" + checkDataStore(dataStore) + "\" file located at " +
                            relativePath.toString() +
                            " directory in your database to migrate the schema with the latest changes.");
                } else {
                    errStream.println("ERROR: Could not find any entities in the schema file");
                }

            } else {
                // Migrate with the latest bal file in the migrations directory
                migrateWithTimestamp(migrationsDir, migrationName, schemaFilePath,
                        findLatestBalFile(getDirectoryPaths(migrationsDir.toString()), sourcePath), dataStore);
            }
        }
    }

    // Get a list of all timestamp folders in the migrations directory
    private static List<String> getDirectoryPaths(String folder) {
        List<String> directoryNames = new ArrayList<>();
        Path folderPath = Path.of(folder);

        if (Files.exists(folderPath) && Files.isDirectory(folderPath)) {
            try (Stream<Path> directoryStream = Files.list(folderPath)) {
                List<Path> directories = directoryStream
                        .filter(Files::isDirectory)
                        .collect(Collectors.toList());

                for (Path directory : directories) {
                    Path fileName = directory.getFileName();
                    if (fileName != null) {
                        directoryNames.add(fileName.toString());
                    } else {
                        errStream.println("Error: Found a directory with a null file name.");
                    }
                }
            } catch (IOException e) {
                errStream.println("Error: An error occurred while retrieving"
                        + " the directory paths: " + e.getMessage());
            }
        }

        return directoryNames;
    }

    // Get the path of the latest .bal file in the migrations directory
    private static Path findLatestBalFile(List<String> folderNames, String sourcePath) {
        if (folderNames.size() == 1) {
            return findBalFileInFolder(folderNames.get(0), sourcePath);
        }

        String latestTimestamp = "";
        ZonedDateTime latestDateTime = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

        for (String folderName : folderNames) {
            String timestamp = folderName.split("_")[0];
            ZonedDateTime dateTime = ZonedDateTime.parse(timestamp, formatter);

            if (dateTime.isAfter(latestDateTime)) {
                latestDateTime = dateTime;
                latestTimestamp = folderName;
            }
        }

        return findBalFileInFolder(latestTimestamp, sourcePath);
    }

    // Find the .bal file in the given folder
    private static Path findBalFileInFolder(String folderName, String sourcePath) {
        Path folderPath = Paths.get(sourcePath).resolve("persist")
                .resolve("migrations").resolve(folderName).toAbsolutePath();
        File folder = folderPath.toFile();
        File[] files = folder.listFiles();

        if (files != null) {
            // Use lambda function to filter the files and find the one ending with ".bal"
            File balFile = Arrays.stream(files)
                    .filter(file -> file.isFile() && file.getName().endsWith(".bal"))
                    .findFirst()
                    .orElse(null);

            if (balFile != null) {
                // Return the Path object of the .bal file
                return balFile.toPath().toAbsolutePath();
            }
        }

        // No .bal file found
        return null;
    }

    private static void migrateWithTimestamp(File migrationsDir, String migrationName, Path currentModelPath,
            Path previousModelPath, String dataStore) {
        List<String> queries;

        String folderName = createTimestampFolder(migrationName);
        String newMigration = migrationsDir + File.separator + folderName;

        File newMigrateDirectory = getTimestampDirectory(newMigration);

        Path newMigrationPath = Paths.get(newMigration);
        try {
            Module previousModel = BalProjectUtils.getEntities(previousModelPath);
            Module currentModel = BalProjectUtils.getEntities(currentModelPath);

            queries = findDifferences(previousModel, currentModel, dataStore);

            // Write queries to file
            if (!queries.isEmpty()) {
                String filePath = Paths.get(newMigrationPath.toString(), folderName + "_script" + 
                    checkDataStore(dataStore)).toString();
                try (FileOutputStream fStream = new FileOutputStream(filePath);
                        OutputStreamWriter oStream = new OutputStreamWriter(fStream, StandardCharsets.UTF_8);
                        BufferedWriter writer = new BufferedWriter(oStream)) {
                    switch(dataStore) {
                        case "mysql":
                            writer.write("-- AUTO-GENERATED FILE." + System.lineSeparator() +
                            "-- This file is an auto-generated file by Ballerina " +
                            "persistence layer for the migrate command." + System.lineSeparator() +
                            "-- Please verify the generated scripts and " +
                            "execute them against the target DB server." +
                            System.lineSeparator() + System.lineSeparator());
                            break;

                        case "googlesheets":
                            writer.write("/*" + System.lineSeparator() + 
                            "AUTO-GENERATED FILE." + System.lineSeparator() + System.lineSeparator() +
                            "This file is an auto-generated file by Ballerina " +
                            "persistence layer for the migrate command." + System.lineSeparator() +
                            "Please verify the generated scripts and " +
                            "execute them against the target DB server." +
                            System.lineSeparator() + "*/" +
                            System.lineSeparator() + System.lineSeparator() +
                            "function migrateSheets() {" + System.lineSeparator() +
                            "   var activeSpreadsheet = SpreadsheetApp.getActiveSpreadsheet();" +
                            "   var yourNewSheet = \"\"");
                            break;

                        default:
                            errStream.println("Error: Invalid data store: " + dataStore);
                            break;
                    }

                    for (String query : queries) {
                        writer.write(query);
                        writer.newLine();
                    }

                    if (dataStore.equals("googlesheets")) {
                        writer.write(
                            System.lineSeparator() + " yourNewSheet  = activeSpreadsheet.getSheetByName(\"Sheet1\");" +
                            System.lineSeparator() + "  if (yourNewSheet != null) {" + 
                            System.lineSeparator() + "       activeSpreadsheet.deleteSheet(yourNewSheet);" + 
                            System.lineSeparator() + "  }" +
                            System.lineSeparator() + "}" + System.lineSeparator());
                    }

                } catch (IOException e) {
                    errStream.println("Error: An error occurred while writing to file: " + e.getMessage());
                    return;
                }

                //Get the relative path of the migration directory from the project root
                Path relativePath = Paths.get("").toAbsolutePath().relativize(newMigrationPath);

                errStream.println(
                        "Generated migration script to " + relativePath.toString() + 
                        " directory." + System.lineSeparator());
                errStream.println("Next steps:" + System.lineSeparator() + "Execute the \"script" + 
                    checkDataStore(dataStore) + "\" file located at "
                        + relativePath.toString() +
                        " directory in your database to migrate the schema with the latest changes.");
            }

        } catch (BalException e) {
            errStream.println("Error getting entities: " + e.getMessage());
            return;
        }

        if (!queries.isEmpty()) {
            try {
                // Copy the source file to the destination folder
                Files.copy(currentModelPath, newMigrationPath.resolve(currentModelPath.getFileName()));
            } catch (IOException e) {
                errStream.println("Error: Copying file failed: " + e.getMessage());
                return;
            }
        } else {
            // Delete the newMigrateDirectory
            if (newMigrateDirectory != null) {
                boolean deleteNewMigrateFolder = newMigrateDirectory.delete();
                if (!deleteNewMigrateFolder) {
                    errStream.println("Error: Failed to delete timestamp folder.");
                }
            }

            // Delete the migrations directory if it is empty
            if (migrationsDir.exists() && migrationsDir.isDirectory()) {
                File[] files = migrationsDir.listFiles();
                if (files != null && files.length == 0) {
                    try {
                        Files.delete(migrationsDir.toPath());
                    } catch (IOException e) {
                        errStream.println("Error: Failed to delete migration folder: " + e.getMessage());
                        return;
                    }
                }
            }
        }
    }

    // Create a timestamp folder name
    private static String createTimestampFolder(String migrationName) {
        Instant currentTime = Instant.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String timestamp = formatter.format(ZonedDateTime.ofInstant(currentTime, ZoneOffset.UTC));

        String newMigration = timestamp + "_" + migrationName;

        return newMigration;
    }

    // Create the timestamp directory
    private static File getTimestampDirectory(String newMigration) {
        File newMigrateDirectory = new File(newMigration);

        if (!newMigrateDirectory.exists()) {
            boolean isDirectoryCreated = newMigrateDirectory.mkdirs();
            if (!isDirectoryCreated) {
                errStream.println("Error: Failed to create directory: " + newMigrateDirectory.getAbsolutePath());
            } else {
                return newMigrateDirectory;
            }
        }

        return null;
    }

    // Create the timestamp directory without returning the directory
    private static void createTimestampDirectory(String newMigration) {
        File newMigrateDirectory = new File(newMigration);

        if (!newMigrateDirectory.exists()) {
            boolean isDirectoryCreated = newMigrateDirectory.mkdirs();
            if (!isDirectoryCreated) {
                errStream.println("Error: Failed to create directory: " + newMigrateDirectory.getAbsolutePath());
            }
        }
    }

    private static List<String> findDifferences(Module previousModel, Module currentModel, String dataStore) {
        List<String> queries = new ArrayList<>();
        List<String> differences = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        List<String> addedEntities = new ArrayList<>();
        List<String> removedEntities = new ArrayList<>();
        HashMap<String, List<FieldMetadata>> addedFields = new HashMap<>();
        HashMap<String, List<String>> removedFields = new HashMap<>();
        HashMap<String, List<FieldMetadata>> changedFieldTypes = new HashMap<>();
        HashMap<String, List<FieldMetadata>> addedReadOnly = new HashMap<>();
        HashMap<String, List<String>> removedReadOnly = new HashMap<>();
        HashMap<String, List<ForeignKey>> addedForeignKeys = new HashMap<>();
        HashMap<String, List<String>> removedForeignKeys = new HashMap<>();

        // Compare entities in previousModel and currentModel
        for (Entity previousModelEntity : previousModel.getEntityMap().values()) {
            Entity currentModelEntity = currentModel.getEntityMap().get(previousModelEntity.getEntityName());

            // Check if currentModelEntity exists
            if (currentModelEntity == null) {
                differences.add("Entity " + previousModelEntity.getEntityName() + " has been removed");
                removedEntities.add(previousModelEntity.getEntityName());
                continue;
            }

            // Compare fields in previousModelEntity and currentModelEntity
            for (EntityField previousModelField : previousModelEntity.getFields()) {
                EntityField currentModelField = currentModelEntity.getFieldByName(previousModelField.getFieldName());

                // Check if currentModelField exists and if foreign key was removed
                if (currentModelField == null) {
                    if (previousModelField.getRelation() == null) {
                        differences.add("Field " + previousModelField.getFieldName() +
                                " has been removed from entity " + previousModelEntity.getEntityName());
                        addToMapNoTypeString(previousModelEntity, previousModelField, removedFields);
                    } else if (previousModelField.getRelation().isOwner()) {
                        differences.add("Foreign key " + previousModelField.getFieldName() +
                                " has been removed from entity " + previousModelEntity.getEntityName());
                        addToMapRemoveForeignKey(previousModelEntity, previousModelField, removedForeignKeys);
                    }
                    continue;
                }

                // Compare data types
                if (!previousModelField.getFieldType().equals(currentModelField.getFieldType())) {
                    differences.add("Data type of field " + previousModelField.getFieldName() +
                            " in entity " + previousModelEntity.getEntityName() + " has changed from " +
                            previousModelField.getFieldType() + " to " + currentModelField.getFieldType());
                    addToMapWithType(previousModelEntity, currentModelField, changedFieldTypes);
                }

                // Compare readonly fields
                if (previousModelEntity.getKeys().contains(previousModelField)
                        && !currentModelEntity.getKeys().contains(currentModelField)) {
                    differences.add("Field " + previousModelField.getFieldName() + " in entity " +
                            previousModelEntity.getEntityName() + " is no longer a readonly field");
                    addToMapNoTypeString(previousModelEntity, previousModelField, removedReadOnly);

                } else if (!previousModelEntity.getKeys().contains(previousModelField)
                        && currentModelEntity.getKeys().contains(currentModelField)) {
                    differences.add("Field " + previousModelField.getFieldName() + " in entity " +
                            previousModelEntity.getEntityName() + " is now a readonly field");
                    addToMapNoTypeObject(previousModelEntity, currentModelField, addedReadOnly);
                }

            }

            // Check for added fields and for added foreign keys
            for (EntityField currentModelField : currentModelEntity.getFields()) {
                EntityField previousModelField = previousModelEntity.getFieldByName(currentModelField.getFieldName());

                if (previousModelField == null) {
                    if (currentModelField.getRelation() == null) {
                        if (currentModelEntity.getKeys().contains(currentModelField)) {
                            differences.add("Field " + currentModelField.getFieldName() + " of type " +
                                    currentModelField.getFieldType() + " has been added to entity " +
                                    currentModelEntity.getEntityName() + " as a readonly field");
                            addToMapNoTypeObject(currentModelEntity, currentModelField, addedReadOnly);
                        } else {
                            differences.add("Field " + currentModelField.getFieldName() + " of type " +
                                    currentModelField.getFieldType() + " has been added to entity " +
                                    currentModelEntity.getEntityName());
                        }
                        addToMapWithType(currentModelEntity, currentModelField, addedFields);
                    } else if (currentModelField.getRelation().isOwner()) {
                        differences.add("Field " + currentModelField.getRelation().getKeyColumns().get(0).getField() +
                                " of type " + currentModelField.getRelation().getKeyColumns().get(0).getType() +
                                " has been added to entity " + currentModelEntity.getEntityName()
                                + " as a foreign key");
                        addToMapNewEntityFK(currentModelEntity, currentModelField, addedFields);

                        differences.add("Foreign key " + currentModelField.getFieldName() + " of type " +
                                currentModelField.getFieldType() + " has been added to entity " +
                                currentModelEntity.getEntityName());
                        addToMapAddForeignKey(currentModelEntity, currentModelField, addedForeignKeys);
                    }
                }
            }
        }

        // Check for added entities
        for (Entity currentModelEntity : currentModel.getEntityMap().values()) {
            Entity previousModelEntity = previousModel.getEntityMap().get(currentModelEntity.getEntityName());

            if (previousModelEntity == null) {
                differences.add("Entity " + currentModelEntity.getEntityName() + " has been added");
                addedEntities.add(currentModelEntity.getEntityName());
                for (EntityField field : currentModelEntity.getFields()) {
                    if (field.getRelation() == null) {
                        if (currentModelEntity.getKeys().contains(field)) {
                            differences.add("Field " + field.getFieldName() + " of type " +
                                    field.getFieldType() + " has been added to entity " +
                                    currentModelEntity.getEntityName() + " as a readonly field");
                            addToMapWithType(currentModelEntity, field, addedReadOnly);

                        } else {
                            differences.add("Field " + field.getFieldName() + " of type " +
                                    field.getFieldType() + " has been added to entity " +
                                    currentModelEntity.getEntityName());
                            addToMapWithType(currentModelEntity, field, addedFields);
                        }
                    } else if (field.getRelation().isOwner()) {
                        differences.add("Field " + field.getRelation().getKeyColumns().get(0).getField() +
                                " of type " + field.getRelation().getKeyColumns().get(0).getType() +
                                " has been added to entity " + currentModelEntity.getEntityName()
                                + " as a foreign key");
                        addToMapNewEntityFK(currentModelEntity, field, addedFields);

                        differences.add("Foreign key " + field.getFieldName() + " of type " +
                                field.getFieldType() + " has been added to entity "
                                + currentModelEntity.getEntityName());
                        addToMapAddForeignKey(currentModelEntity, field, addedForeignKeys);
                    }
                }
            }
        }

        // Convert differences to queries (ordered) based on datasource
        switch (dataStore) {
            case("mysql"):
                convertCreateTableToMySQLQuery(QueryTypes.ADD_TABLE, addedEntities, queries, 
                    addedReadOnly, addedFields);
                convertMapToMySQLQuery(QueryTypes.ADD_FIELD, addedFields, queries, addedEntities);
                convertMapListToMySQLQuery(QueryTypes.REMOVE_FOREIGN_KEY, removedForeignKeys, queries);
                convertMapListToMySQLQuery(QueryTypes.REMOVE_READONLY, removedReadOnly, queries);
                convertMapToMySQLQuery(QueryTypes.ADD_READONLY, addedReadOnly, queries, addedEntities);
                convertFKMapToMySQLQuery(QueryTypes.ADD_FOREIGN_KEY, addedForeignKeys, queries);
                convertMapListToMySQLQuery(QueryTypes.REMOVE_FIELD, removedFields, queries);
                convertListToMySQLQuery(QueryTypes.REMOVE_TABLE, removedEntities, queries);
                convertMapToMySQLQuery(QueryTypes.CHANGE_TYPE, changedFieldTypes, queries, addedEntities);
                break;

            case("googlesheets"):
                convertCreateTableToGSheetsQuery(QueryTypes.ADD_TABLE, 
                    addedEntities, queries, addedReadOnly, addedFields);
                List<String> addFieldWarnings = convertMapToGSheetsQuery(QueryTypes.ADD_FIELD, 
                    addedFields, queries, addedEntities);
                for (String warning : addFieldWarnings) {
                    warnings.add(warning);
                }
                List<String> removeFKWarnings = convertMapListToGSheetsQuery(QueryTypes.REMOVE_FOREIGN_KEY, 
                    removedForeignKeys, queries);
                for (String warning : removeFKWarnings) {
                    warnings.add(warning);
                }
                List<String> removePKWarnings = convertMapListToGSheetsQuery(QueryTypes.REMOVE_READONLY, 
                    removedReadOnly, queries);
                for (String warning : removePKWarnings) {
                    warnings.add(warning);
                }
                List<String> addReadOnlyWarnings = convertMapToGSheetsQuery(QueryTypes.ADD_READONLY, 
                    addedReadOnly, queries, addedEntities);
                for (String warning : addReadOnlyWarnings) {
                    warnings.add(warning);
                }
                List<String> addFKWarnings = convertFKMapToGSheetsQuery(QueryTypes.ADD_FOREIGN_KEY, 
                    addedForeignKeys, queries);
                for (String warning : addFKWarnings) {
                    warnings.add(warning);
                }
                List<String> removeFieldWarnings = convertMapListToGSheetsQuery(QueryTypes.REMOVE_FIELD, 
                    removedFields, queries);
                for (String warning : removeFieldWarnings) {
                    warnings.add(warning);
                }
                List<String> removeTableWarnings = convertListToGSheetsQuery(QueryTypes.REMOVE_TABLE, 
                    removedEntities, queries);
                for (String warning : removeTableWarnings) {
                    warnings.add(warning);
                }
                List<String> changeDataTypeWarnings = convertMapToGSheetsQuery(QueryTypes.CHANGE_TYPE, 
                    changedFieldTypes, queries, addedEntities);
                for (String warning : changeDataTypeWarnings) {
                    warnings.add(warning);
                }
                break;

            default:
                errStream.println("Error: Invalid data store: " + dataStore);
                break;
        }

        errStream.println(System.lineSeparator() + "Detailed list of differences: ");
        if (!differences.isEmpty()) {
            errStream.println(differences + System.lineSeparator());
        } else {
            errStream.println("[No differences found]" + System.lineSeparator());
        }

        if (!warnings.isEmpty()) {
            errStream.println(System.lineSeparator() + "List of Warnings: ");
            errStream.println(warnings + System.lineSeparator());
        }

        return queries;
    }

    private static void addToMapAddForeignKey(Entity entity, EntityField field, Map<String, List<ForeignKey>> map) {
        String addKeyName = String.format("FK_%s_%s", entity.getEntityName(),
                field.getRelation().getAssocEntity().getEntityName());
        ForeignKey foreignKey = new ForeignKey(addKeyName, field.getRelation().getKeyColumns().get(0).getField(),
                field.getRelation().getAssocEntity().getEntityName(),
                field.getRelation().getKeyColumns().get(0).getReference());

        if (!map.containsKey(entity.getEntityName())) {
            List<ForeignKey> initialData = new ArrayList<>();
            initialData.add(foreignKey);
            map.put(entity.getEntityName(), initialData);
        } else {
            List<ForeignKey> existingData = map.get(entity.getEntityName());
            existingData.add(foreignKey);
            map.put(entity.getEntityName(), existingData);
        }
    }

    private static void addToMapRemoveForeignKey(Entity entity, EntityField field, Map<String, List<String>> map) {
        String removeKeyName = String.format("FK_%s_%s", entity.getEntityName(),
                field.getRelation().getAssocEntity().getEntityName());

        if (!map.containsKey(entity.getEntityName())) {
            List<String> initialData = new ArrayList<>();
            initialData.add(removeKeyName);
            map.put(entity.getEntityName(), initialData);
        } else {
            List<String> existingData = map.get(entity.getEntityName());
            existingData.add(removeKeyName);
            map.put(entity.getEntityName(), existingData);
        }
    }

    private static void addToMapNoTypeString(Entity entity, EntityField field, Map<String, List<String>> map) {
        if (!map.containsKey(entity.getEntityName())) {
            List<String> initialData = new ArrayList<>();
            initialData.add(field.getFieldName());
            map.put(entity.getEntityName(), initialData);
        } else {
            List<String> existingData = map.get(entity.getEntityName());
            existingData.add(field.getFieldName());
            map.put(entity.getEntityName(), existingData);
        }
    }

    private static void addToMapNoTypeObject(Entity entity, EntityField field, Map<String, List<FieldMetadata>> map) {
        FieldMetadata fieldMetadata = new FieldMetadata(field.getFieldName());

        if (!map.containsKey(entity.getEntityName())) {
            List<FieldMetadata> initialData = new ArrayList<>();
            initialData.add(fieldMetadata);
            map.put(entity.getEntityName(), initialData);
        } else {
            List<FieldMetadata> existingData = map.get(entity.getEntityName());
            existingData.add(fieldMetadata);
            map.put(entity.getEntityName(), existingData);
        }
    }

    private static void addToMapWithType(Entity entity, EntityField field, Map<String, List<FieldMetadata>> map) {
        FieldMetadata fieldMetadata = new FieldMetadata(field.getFieldName(), field.getFieldType(),
                field.isArrayType());

        if (!map.containsKey(entity.getEntityName())) {
            List<FieldMetadata> initialData = new ArrayList<>();
            initialData.add(fieldMetadata);
            map.put(entity.getEntityName(), initialData);
        } else {
            List<FieldMetadata> existingData = map.get(entity.getEntityName());
            existingData.add(fieldMetadata);
            map.put(entity.getEntityName(), existingData);
        }
    }

    private static void addToMapNewEntityFK(Entity entity, EntityField field, Map<String, List<FieldMetadata>> map) {
        FieldMetadata fieldMetadata = new FieldMetadata(field.getRelation().getKeyColumns().get(0).getField(),
                field.getRelation().getKeyColumns().get(0).getType(), field.isArrayType());

        if (!map.containsKey(entity.getEntityName())) {
            List<FieldMetadata> initialData = new ArrayList<>();
            initialData.add(fieldMetadata);
            map.put(entity.getEntityName(), initialData);
        } else {
            List<FieldMetadata> existingData = map.get(entity.getEntityName());
            existingData.add(fieldMetadata);
            map.put(entity.getEntityName(), existingData);
        }
    }

    // Convert Create Table List to MySQL Query
    private static void convertCreateTableToMySQLQuery(QueryTypes type, List<String> addedEntities, 
        List<String> queries, HashMap<String, List<FieldMetadata>> addedReadOnly, HashMap<String, 
        List<FieldMetadata>> addedFields) {
        String addField = "";
        String pKField = "";
        if (Objects.requireNonNull(type) == QueryTypes.ADD_TABLE) {
            for (String entity : addedEntities) {
                FieldMetadata primaryKey = addedReadOnly.get(entity).get(0);
                String addTableTemplate = "CREATE TABLE %s (%n    %s %s PRIMARY KEY";

                try {
                    if (!primaryKey.isArrayType()) {
                        pKField = SqlScriptUtils.getTypeNonArray(primaryKey.getDataType(),
                                PersistToolsConstants.SupportedDataSources.MYSQL_DB);
                    } else {
                        pKField = SqlScriptUtils.getTypeArray(primaryKey.getDataType(),
                                PersistToolsConstants.SupportedDataSources.MYSQL_DB);
                    }
                } catch (BalException e) {
                    errStream.println("ERROR: data type conversion failed: " + e.getMessage());
                    return;
                }

                StringBuilder query = new StringBuilder(
                        String.format(addTableTemplate, entity, primaryKey.getName(), pKField));

                String addFieldTemplate = ",%n    %s %s";

                if (addedFields.get(entity) != null) {
                    for (FieldMetadata field : addedFields.get(entity)) {
                        try {
                            if (!field.isArrayType()) {
                                addField = SqlScriptUtils.getTypeNonArray(field.getDataType(),
                                        PersistToolsConstants.SupportedDataSources.MYSQL_DB);
                            } else {
                                addField = SqlScriptUtils.getTypeArray(field.getDataType(),
                                        PersistToolsConstants.SupportedDataSources.MYSQL_DB);
                            }
                        } catch (BalException e) {
                            errStream.println("ERROR: data type conversion failed: " + e.getMessage());
                            return;
                        }

                        query.append(String.format(addFieldTemplate, field.getName(), addField));
                    }
                }

                query.append("\n);\n");
                queries.add(query.toString());
            }
        }
    }

    // Convert list to a MySQL query
    private static void convertListToMySQLQuery(QueryTypes type, List<String> entities, List<String> queries) {
        if (Objects.requireNonNull(type) == QueryTypes.REMOVE_TABLE) {
            for (String entity : entities) {
                String removeTableTemplate = "DROP TABLE %s;%n";
                queries.add(String.format(removeTableTemplate, entity));
            }
        }
    }

    // Convert map of String lists to a MySQL query
    private static void convertMapListToMySQLQuery(QueryTypes type, Map<String, 
    List<String>> map, List<String> queries) {
        switch (type) {
            case REMOVE_FIELD:
                for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                    String entity = entry.getKey();
                    for (String field : entry.getValue()) {
                        String removeFieldTemplate = "ALTER TABLE %s%nDROP COLUMN %s;%n";
                        queries.add(String.format(removeFieldTemplate, entity, field));
                    }
                }
                break;

            case REMOVE_READONLY:
                for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                    String entity = entry.getKey();
                    String removeReadOnlyTemplate = "ALTER TABLE %s%nDROP PRIMARY KEY;%n";
                    queries.add(String.format(removeReadOnlyTemplate, entity));
                }
                break;

            case REMOVE_FOREIGN_KEY:
                for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                    String entity = entry.getKey();
                    for (String field : entry.getValue()) {
                        String[] fieldData = field.split(",");
                        String foreignKeyName = fieldData[0];
                        String removeForeignKeyTemplate = "ALTER TABLE %s%nDROP FOREIGN KEY %s;%n";
                        queries.add(String.format(removeForeignKeyTemplate, entity, foreignKeyName));
                    }
                }
                break;

            default:
                break;
        }
    }

    // Convert map of FieldMetadata lists to a MySQL query
    private static void convertMapToMySQLQuery(QueryTypes type, Map<String, 
        List<FieldMetadata>> map, List<String> queries, List<String> addedEntities) {
        switch (type) {
            case ADD_FIELD:
                for (Map.Entry<String, List<FieldMetadata>> entry : map.entrySet()) {
                    String entity = entry.getKey();
                    if (!addedEntities.contains(entity)) {
                        for (FieldMetadata field : entry.getValue()) {
                            String fieldName = field.getName();
                            String fieldType = "";
                            try {
                                if (!field.isArrayType()) {
                                    fieldType = SqlScriptUtils.getTypeNonArray(field.getDataType(),
                                            PersistToolsConstants.SupportedDataSources.MYSQL_DB);
                                } else {
                                    fieldType = SqlScriptUtils.getTypeArray(field.getDataType(),
                                            PersistToolsConstants.SupportedDataSources.MYSQL_DB);
                                }
                            } catch (BalException e) {
                                errStream.println("ERROR: data type conversion failed: " + e.getMessage());
                                return;
                            }
                            String addFieldTemplate = "ALTER TABLE %s%nADD COLUMN %s %s;%n";

                            queries.add(String.format(addFieldTemplate, entity, fieldName, fieldType));
                        }
                    }
                }
                break;

            case CHANGE_TYPE:
                for (Map.Entry<String, List<FieldMetadata>> entry : map.entrySet()) {
                    String entity = entry.getKey();
                    for (FieldMetadata field : entry.getValue()) {
                        String fieldName = field.getName();
                        String fieldType = "";
                        try {
                            if (!field.isArrayType()) {
                                fieldType = SqlScriptUtils.getTypeNonArray(field.getDataType(),
                                        PersistToolsConstants.SupportedDataSources.MYSQL_DB);
                            } else {
                                fieldType = SqlScriptUtils.getTypeArray(field.getDataType(),
                                        PersistToolsConstants.SupportedDataSources.MYSQL_DB);
                            }
                        } catch (BalException e) {
                            errStream.println("ERROR: data type conversion failed: " + e.getMessage());
                            return;
                        }
                        String changeTypeTemplate = "ALTER TABLE %s%nMODIFY COLUMN %s %s;%n";

                        queries.add(String.format(changeTypeTemplate, entity, fieldName, fieldType));
                    }
                }
                break;

            case ADD_READONLY:
                for (Map.Entry<String, List<FieldMetadata>> entry : map.entrySet()) {
                    String entity = entry.getKey();
                    if (!addedEntities.contains(entity)) {
                        for (FieldMetadata field : entry.getValue()) {
                            String primaryKey = field.getName();
                            String addReadOnlyTemplate = "ALTER TABLE %s%nADD PRIMARY KEY (%s);%n";

                            queries.add(String.format(addReadOnlyTemplate, entity, primaryKey));
                        }
                    }
                }
                break;

            default:
                break;
        }
    }

    // Convert map of ForeignKey lists to a MySQL query
    private static void convertFKMapToMySQLQuery(QueryTypes type, Map<String, 
        List<ForeignKey>> map, List<String> queries) {
        if (Objects.requireNonNull(type) == QueryTypes.ADD_FOREIGN_KEY) {
            for (Map.Entry<String, List<ForeignKey>> entry : map.entrySet()) {
                String entity = entry.getKey();
                for (ForeignKey foreignKey : entry.getValue()) {

                    String foreignKeyName = foreignKey.getName();
                    String childColumnName = foreignKey.getColumnName();
                    String referenceTableName = foreignKey.getReferenceTable();
                    String referenceColumnName = foreignKey.getReferenceColumn();
                    String addFKTemplate = "ALTER TABLE %s%nADD CONSTRAINT %s FOREIGN KEY (%s) REFERENCES %s(%s);%n";

                    queries.add(String.format(addFKTemplate, entity, foreignKeyName, childColumnName,
                            referenceTableName, referenceColumnName));
                }
            }
        }
    }

    // Convert Create Table List to Google Sheets Query
    private static void convertCreateTableToGSheetsQuery(QueryTypes type, List<String> addedEntities, 
        List<String> queries, HashMap<String, List<FieldMetadata>> addedReadOnly, HashMap<String, 
        List<FieldMetadata>> addedFields) {
        if (Objects.requireNonNull(type) == QueryTypes.ADD_TABLE) {
            for (String entity : addedEntities) {
                StringBuilder query = new StringBuilder(
                System.lineSeparator() +
                System.lineSeparator() + "  yourNewSheet = activeSpreadsheet.getSheetByName(\"" + entity + "\");" + 
                System.lineSeparator() + "  if (yourNewSheet != null) {" +
                System.lineSeparator() + "      activeSpreadsheet.deleteSheet(yourNewSheet);" +
                System.lineSeparator() + "  }" + 
                System.lineSeparator() + "  yourNewSheet = activeSpreadsheet.insertSheet();" +
                System.lineSeparator() + "  yourNewSheet.setName(\"" + entity + "\");");

                if (addedFields.get(entity) != null) {
                    query.append(System.lineSeparator() + "  yourNewSheet.appendRow([");
                    for (FieldMetadata field : addedFields.get(entity)) {
                        if (addedFields.get(entity).indexOf(field) == addedFields.get(entity).size() - 1) {
                            query.append("\"" + field.getName() + "\"");
                        } else {
                            query.append("\"" + field.getName() + "\", ");
                        }
                    }
                }

                query.append("\n);\n");
                queries.add(query.toString());
            }
        }
    }

    // Convert list to a Google Sheets query
    private static List<String> convertListToGSheetsQuery(QueryTypes type, List<String> entities, 
        List<String> queries) {
        List<String> warnings = new ArrayList<>();
        String warning = "";
        if (Objects.requireNonNull(type) == QueryTypes.REMOVE_TABLE) {
            for (String entity : entities) {
                StringBuilder query = new StringBuilder(
                    System.lineSeparator() + "  yourNewSheet  = activeSpreadsheet.getSheetByName(\"" + entity + "\");" +
                    System.lineSeparator() + "  if (yourNewSheet != null) {" +
                    System.lineSeparator() + "      activeSpreadsheet.deleteSheet(yourNewSheet);" +
                    System.lineSeparator() + "  }"
                );

                query.append("\n);\n");
                queries.add(query.toString());

                warning = "WARNING: User must manually delete any foreign keys related to the deleted table.\n";
                warnings.add(warning);
            }
        }

        return warnings;
    }

    // Convert map of FieldMetadata lists to a Google Sheets query
    private static List<String> convertMapToGSheetsQuery(QueryTypes type, Map<String, List<FieldMetadata>> map, 
        List<String> queries,
        List<String> addedEntities) {
        List<String> warnings = new ArrayList<>();
        String warning = "";
        switch (type) {
            case ADD_FIELD:
                for (Map.Entry<String, List<FieldMetadata>> entry : map.entrySet()) {
                    String entity = entry.getKey();
                    if (!addedEntities.contains(entity)) {
                        for (FieldMetadata field : entry.getValue()) {
                            StringBuilder query = new StringBuilder(
                                System.lineSeparator() + "  yourNewSheet  = activeSpreadsheet.getSheetByName(\"" + 
                                entity + "\");" +
                                System.lineSeparator() + "  var numColumns = yourSheet.getLastColumn();" +
                                System.lineSeparator() + "  var newColumn = numColumns + 1;" +
                                System.lineSeparator() + "  yourSheet.getRange(1, newColumn).setValue(\"" + 
                                field.getName() + "\");"
                            );

                            query.append("\n);\n");

                            queries.add(query.toString());
                        }
                    }
                }
                break;

            case CHANGE_TYPE:
                for (Map.Entry<String, List<FieldMetadata>> entry : map.entrySet()) {
                    String entity = entry.getKey();
                    for (FieldMetadata field : entry.getValue()) {
                        warning = "WARNING: User must manually change the data type of " +
                        "the field " + field.getName() + " in the " + entity + " table.";
                        warnings.add(warning);
                    }
                }
                break;

            case ADD_READONLY:
                for (Map.Entry<String, List<FieldMetadata>> entry : map.entrySet()) {
                    String entity = entry.getKey();
                    if (!addedEntities.contains(entity)) {
                        warning = "WARNING: Google Sheets does not support primary keys";
                        warnings.add(warning);
                    }
                }
                break;

            default:
                break;
        }

        return warnings;
    }

    // Convert map of String lists to a Google Sheets query
    private static List<String> convertMapListToGSheetsQuery(QueryTypes type, Map<String, 
        List<String>> map, List<String> queries) {
        List<String> warnings = new ArrayList<>();
        String warning = "";
        switch (type) {
            case REMOVE_FIELD:
                for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                    String entity = entry.getKey();
                    for (String field : entry.getValue()) {
                        StringBuilder query = new StringBuilder(
                                System.lineSeparator() + "  yourNewSheet  = activeSpreadsheet.getSheetByName(\"" + 
                                entity + "\");" +
                                System.lineSeparator() + "  var headerRange = yourSheet.getRange(1, 1, 1, "
                                + "yourSheet.getLastColumn());" +
                                System.lineSeparator() + "  var headerValues = headerRange.getValues()[0];" +
                                System.lineSeparator() + "  var fieldToRemove = \"" + field + "\";" +
                                System.lineSeparator() + "  var fieldIndex = headerValues.indexOf(fieldToRemove) + 1;" +
                                System.lineSeparator() + "  if (fieldIndex != 0) {" +
                                System.lineSeparator() + "      yourSheet.deleteColumn(fieldIndex);" +
                                System.lineSeparator() + "  }"
                            );

                        query.append("\n);\n");

                        queries.add(query.toString());
                    }
                }
                break;

            case REMOVE_READONLY:
                for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                    warning = "WARNING: Google Sheets does not support primary keys, " +
                    "User needs to manually remove the column from the " + entry.getKey() + " table.";
                    warnings.add(warning);
                }
                break;

            case REMOVE_FOREIGN_KEY:
                for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                    String entity = entry.getKey();
                    for (String field : entry.getValue()) {
                        String[] fieldData = field.split(",");
                        String foreignKeyName = fieldData[0];
                        warning = "WARNING: User must manually delete the foreign key " + 
                        foreignKeyName + " in the " + entity + " table.";
                        warnings.add(warning);
                    }
                }
                break;

            default:
                break;
        }

        return warnings;
    }

    // Convert map of ForeignKey lists to a Google Sheets query
    private static List<String> convertFKMapToGSheetsQuery(QueryTypes type, Map<String, List<ForeignKey>> map, 
        List<String> queries) {
        List<String> warnings = new ArrayList<>();
        String warning = "";
        if (Objects.requireNonNull(type) == QueryTypes.ADD_FOREIGN_KEY) {
            for (Map.Entry<String, List<ForeignKey>> entry : map.entrySet()) {
                String entity = entry.getKey();
                for (ForeignKey foreignKey : entry.getValue()) {
                    String childColumnName = foreignKey.getColumnName();
                
                    StringBuilder query = new StringBuilder(
                        System.lineSeparator() + "  yourNewSheet  = activeSpreadsheet.getSheetByName(\"" + 
                        entity + "\");" +
                        System.lineSeparator() + "  var numColumns = yourSheet.getLastColumn();" +
                        System.lineSeparator() + "  var newColumn = numColumns + 1;" +
                        System.lineSeparator() + "  yourSheet.getRange(1, newColumn).setValue(\"" + 
                        childColumnName + "\");"
                    );

                    query.append("\n);\n");

                    queries.add(query.toString());

                    warning = "WARNING: When deleting parent table user must manually remove any " +
                    "foreign keys related to the deleted table.";
                    warnings.add(warning);
                }
            }
        }

        return warnings;
    }

    private static String checkDataStore(String dataStore) {
        switch (dataStore) {
            case "mysql":
                return ".sql";
    
            case "googlesheets":
                return ".gs";
    
            default:
                errStream.println("Error: Invalid data store: " + dataStore);
                return "";
        }
    }    
    
    

    // Types of MySQL queries
    private enum QueryTypes {
        ADD_TABLE,
        REMOVE_TABLE,
        ADD_FIELD,
        REMOVE_FIELD,
        CHANGE_TYPE,
        ADD_READONLY,
        REMOVE_READONLY,
        ADD_FOREIGN_KEY,
        REMOVE_FOREIGN_KEY
    }

    @Override
    public void setParentCmdParser(CommandLine parentCmdParser) {

    }

    @Override
    public String getName() {
        return PersistToolsConstants.COMPONENT_IDENTIFIER;
    }

    @Override
    public void printLongDesc(StringBuilder out) {
        out.append("Migrate the Ballerina schema into MySQL").append(System.lineSeparator());
        out.append(System.lineSeparator());
    }

    @Override
    public void printUsage(StringBuilder stringBuilder) {
        stringBuilder.append("  ballerina " + PersistToolsConstants.COMPONENT_IDENTIFIER + " migrate")
                .append(System.lineSeparator());
    }
}
