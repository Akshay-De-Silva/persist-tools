/*
 * Copyright (c) 2023, WSO2 LLC. (https://www.wso2.com) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.ballerina.persist.nodegenerator.syntax.clients;

import io.ballerina.compiler.syntax.tree.AbstractNodeFactory;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.ImportPrefixNode;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeFactory;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.NodeParser;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.persist.PersistToolsConstants;
import io.ballerina.persist.components.Client;
import io.ballerina.persist.components.Function;
import io.ballerina.persist.components.IfElse;
import io.ballerina.persist.components.TypeDescriptor;
import io.ballerina.persist.models.Entity;
import io.ballerina.persist.models.EntityField;
import io.ballerina.persist.models.Module;
import io.ballerina.persist.models.Relation;
import io.ballerina.persist.nodegenerator.syntax.constants.BalSyntaxConstants;
import io.ballerina.persist.nodegenerator.syntax.constants.SyntaxTokenConstants;
import io.ballerina.persist.nodegenerator.syntax.utils.BalSyntaxUtils;

import java.util.List;

/**
 * This class is used to generate the DB client syntax tree.
 *
 * @since 0.3.1
 */
public class DbClientSyntax implements ClientSyntax {

    private final Module entityModule;

    public DbClientSyntax(Module entityModule) {
        this.entityModule = entityModule;
    }

    public NodeList<ImportDeclarationNode> getImports() {
        NodeList<ImportDeclarationNode> imports = BalSyntaxUtils.generateImport(entityModule);
        imports = imports.add(BalSyntaxUtils.getImportDeclarationNode(BalSyntaxConstants.KEYWORD_BALLERINAX,
                PersistToolsConstants.SupportDataSources.MYSQL_DB, null));
        ImportPrefixNode prefix = NodeFactory.createImportPrefixNode(SyntaxTokenConstants.SYNTAX_TREE_AS,
                AbstractNodeFactory.createToken(SyntaxKind.UNDERSCORE_KEYWORD));
        imports = imports.add(BalSyntaxUtils.getImportDeclarationNode(BalSyntaxConstants.KEYWORD_BALLERINAX,
                BalSyntaxConstants.MYSQL_DRIVER, prefix));
        return imports;
    }

    public NodeList<ModuleMemberDeclarationNode> getConstantVariables() {
        return BalSyntaxUtils.generateConstantVariables(entityModule);
    }

    @Override
    public Client getClientObject(Module entityModule) {
        Client clientObject = BalSyntaxUtils.generateClientSignature();
        clientObject.addMember(NodeParser.parseObjectMember(BalSyntaxConstants.INIT_DB_CLIENT), true);
        clientObject.addMember(NodeParser.parseObjectMember(BalSyntaxConstants.INIT_DB_CLIENT_MAP), true);
        clientObject.addMember(generateMetadataRecord(entityModule), true);
        return clientObject;
    }

    @Override
    public FunctionDefinitionNode getInitFunction(Module entityModule) {
        Function init = new Function(BalSyntaxConstants.INIT, SyntaxKind.OBJECT_METHOD_DEFINITION);
        init.addQualifiers(new String[] { BalSyntaxConstants.KEYWORD_PUBLIC });
        init.addReturns(TypeDescriptor.getOptionalTypeDescriptorNode(BalSyntaxConstants.EMPTY_STRING,
                BalSyntaxConstants.PERSIST_ERROR));
        init.addStatement(NodeParser.parseStatement(BalSyntaxConstants.INIT_DB_CLIENT_WITH_PARAMS));
        IfElse errorCheck = new IfElse(NodeParser.parseExpression(String.format(
                BalSyntaxConstants.RESULT_IS_BALLERINA_ERROR, BalSyntaxConstants.DB_CLIENT)));
        errorCheck.addIfStatement(NodeParser.parseStatement(String.format(BalSyntaxConstants.RETURN_ERROR,
                BalSyntaxConstants.DB_CLIENT)));
        init.addIfElseStatement(errorCheck.getIfElseStatementNode());
        init.addStatement(NodeParser.parseStatement(BalSyntaxConstants.ADD_CLIENT));
        StringBuilder persistClientMap = new StringBuilder();
        for (Entity entity : entityModule.getEntityMap().values()) {
            if (persistClientMap.length() != 0) {
                persistClientMap.append(BalSyntaxConstants.NEWLINE);
            }
            persistClientMap.append(String.format(BalSyntaxConstants.PERSIST_CLIENT_MAP_ELEMENT,
                    BalSyntaxUtils.getStringWithUnderScore(entity.getEntityName()),
                    BalSyntaxUtils.getStringWithUnderScore(entity.getEntityName())));
        }
        init.addStatement(NodeParser.parseStatement(String.format(BalSyntaxConstants.LOCK_TEMPLATE,
                persistClientMap)));
        return init.getFunctionDefinitionNode();
    }

    @Override
    public FunctionDefinitionNode getGetFunction(Entity entity) {
        return BalSyntaxUtils.generateGetFunction(entity, "MySQLProcessor");
    }

    @Override
    public FunctionDefinitionNode getGetByKeyFunction(Entity entity) {
        return BalSyntaxUtils.generateGetByKeyFunction(entity, "MySQLProcessor");
    }

    @Override
    public FunctionDefinitionNode getCloseFunction() {
        Function close = BalSyntaxUtils.generateCloseFunction();
        close.addStatement(NodeParser.parseStatement(BalSyntaxConstants.PERSIST_CLIENT_CLOSE_STATEMENT));
        IfElse errorCheck = new IfElse(NodeParser.parseExpression(String.format(
                BalSyntaxConstants.RESULT_IS_BALLERINA_ERROR, BalSyntaxConstants.RESULT)));
        errorCheck.addIfStatement(NodeParser.parseStatement(String.format(BalSyntaxConstants.RETURN_ERROR,
                BalSyntaxConstants.RESULT)));
        close.addIfElseStatement(errorCheck.getIfElseStatementNode());
        close.addStatement(NodeParser.parseStatement(BalSyntaxConstants.RETURN_RESULT));
        return close.getFunctionDefinitionNode();
    }

    @Override
    public FunctionDefinitionNode getPostFunction(Entity entity) {
        String parameterType = String.format(BalSyntaxConstants.INSERT_RECORD, entity.getEntityName());
        List<EntityField> primaryKeys = entity.getKeys();
        Function create = BalSyntaxUtils.generatePostFunction(entity, primaryKeys, parameterType);
        addFunctionBodyToPostResource(create, primaryKeys,
                BalSyntaxUtils.getStringWithUnderScore(entity.getEntityName()), parameterType);
        return create.getFunctionDefinitionNode();
    }

    @Override
    public FunctionDefinitionNode getPutFunction(Entity entity) {
        StringBuilder filterKeys = new StringBuilder(BalSyntaxConstants.OPEN_BRACE);
        StringBuilder path = new StringBuilder(BalSyntaxConstants.BACK_SLASH + entity.getResourceName());
        Function update = BalSyntaxUtils.generatePutFunction(entity, filterKeys, path);
        String updateStatement;
        if (entity.getKeys().size() > 1) {
            updateStatement = String.format(BalSyntaxConstants.UPDATE_RUN_UPDATE_QUERY,
                    BalSyntaxUtils.getStringWithUnderScore(entity.getEntityName()),
                    filterKeys.substring(0, filterKeys.length() - 2).concat(BalSyntaxConstants.CLOSE_BRACE));
        } else {
            updateStatement = String.format(BalSyntaxConstants.UPDATE_RUN_UPDATE_QUERY,
                    BalSyntaxUtils.getStringWithUnderScore(entity.getEntityName()), entity.getKeys().stream().
                            findFirst().get().getFieldName());
        }
        update.addStatement(NodeParser.parseStatement(
                String.format(BalSyntaxConstants.LOCK_TEMPLATE, updateStatement)));
        update.addStatement(NodeParser.parseStatement(String.format(BalSyntaxConstants.UPDATE_RETURN_UPDATE_QUERY,
                path)));
        return update.getFunctionDefinitionNode();
    }

    @Override
    public FunctionDefinitionNode getDeleteFunction(Entity entity) {
        StringBuilder filterKeys = new StringBuilder(BalSyntaxConstants.OPEN_BRACE);
        StringBuilder path = new StringBuilder(BalSyntaxConstants.BACK_SLASH + entity.getResourceName());
        Function delete = BalSyntaxUtils.generateDeleteFunction(entity, filterKeys, path);
        delete.addStatement(NodeParser.parseStatement(String.format(BalSyntaxConstants.GET_OBJECT_QUERY,
                entity.getEntityName(), path)));
        String deleteStatement;
        if (entity.getKeys().size() > 1) {
            deleteStatement = String.format(BalSyntaxConstants.DELETE_RUN_DELETE_QUERY,
                    BalSyntaxUtils.getStringWithUnderScore(entity.getEntityName()),
                    filterKeys.substring(0, filterKeys.length() - 2).concat(BalSyntaxConstants.CLOSE_BRACE));
        } else {
            deleteStatement = String.format(BalSyntaxConstants.DELETE_RUN_DELETE_QUERY,
                    BalSyntaxUtils.getStringWithUnderScore(entity.getEntityName()), entity.getKeys().stream().
                            findFirst().get().getFieldName());
        }
        delete.addStatement(NodeParser.parseStatement(
                String.format(BalSyntaxConstants.LOCK_TEMPLATE, deleteStatement)));
        delete.addStatement(NodeParser.parseStatement(BalSyntaxConstants.RETURN_DELETED_OBJECT));
        return delete.getFunctionDefinitionNode();
    }

    private static Node generateMetadataRecord(Module entityModule) {
        StringBuilder mapBuilder = new StringBuilder();
        for (Entity entity : entityModule.getEntityMap().values()) {
            if (mapBuilder.length() != 0) {
                mapBuilder.append(BalSyntaxConstants.COMMA_WITH_NEWLINE);
            }
            StringBuilder entityMetaData = new StringBuilder();
            entityMetaData.append(String.format(BalSyntaxConstants.METADATA_RECORD_ENTITY_NAME_TEMPLATE,
                    BalSyntaxUtils.stripEscapeCharacter(entity.getEntityName())));
            entityMetaData.append(String.format(BalSyntaxConstants.METADATA_RECORD_TABLE_NAME_TEMPLATE,
                    BalSyntaxUtils.stripEscapeCharacter(entity.getEntityName())));
            StringBuilder fieldMetaData = new StringBuilder();
            StringBuilder associateFieldMetaData = new StringBuilder();
            boolean relationsExists = false;
            for (EntityField field : entity.getFields()) {
                if (field.getRelation() != null) {
                    relationsExists = true;
                    StringBuilder foreignKeyFields = new StringBuilder();
                    if (field.getRelation().isOwner()) {
                        if (fieldMetaData.length() != 0) {
                            fieldMetaData.append(BalSyntaxConstants.COMMA_WITH_NEWLINE);
                        }
                        for (Relation.Key key : field.getRelation().getKeyColumns()) {
                            if (foreignKeyFields.length() != 0) {
                                foreignKeyFields.append(BalSyntaxConstants.COMMA_WITH_NEWLINE);
                            }
                            foreignKeyFields.append(String.format(BalSyntaxConstants.METADATA_RECORD_FIELD_TEMPLATE,
                                    key.getField(), BalSyntaxUtils.stripEscapeCharacter(key.getField())));
                        }
                    }
                    fieldMetaData.append(foreignKeyFields);
                    Entity associatedEntity = field.getRelation().getAssocEntity();
                    for (EntityField associatedEntityField : associatedEntity.getFields()) {
                        if (associatedEntityField.getRelation() == null) {
                            if (associateFieldMetaData.length() != 0) {
                                associateFieldMetaData.append(BalSyntaxConstants.COMMA_WITH_NEWLINE);
                            }
                            associateFieldMetaData.append(String.format((field.isArrayType() ? "\"%s[]" : "\"%s") +
                                            BalSyntaxConstants.ASSOCIATED_FIELD_TEMPLATE,
                                    field.getFieldName(),
                                    BalSyntaxUtils.stripEscapeCharacter(associatedEntityField.getFieldName()),
                                    BalSyntaxUtils.stripEscapeCharacter(field.getFieldName()),
                                    BalSyntaxUtils.stripEscapeCharacter(associatedEntityField.getFieldName())));
                        } else {
                            if (associatedEntityField.getRelation().isOwner()) {
                                for (Relation.Key key : associatedEntityField.getRelation().getKeyColumns()) {
                                    if (associateFieldMetaData.length() != 0) {
                                        associateFieldMetaData.append(BalSyntaxConstants.COMMA_WITH_NEWLINE);
                                    }
                                    associateFieldMetaData.append(String.format((field.isArrayType() ?
                                                    "\"%s[]" : "\"%s") + BalSyntaxConstants.ASSOCIATED_FIELD_TEMPLATE,
                                            field.getFieldName(),
                                            BalSyntaxUtils.stripEscapeCharacter(key.getField()),
                                            BalSyntaxUtils.stripEscapeCharacter(field.getFieldName()),
                                            BalSyntaxUtils.stripEscapeCharacter(key.getField())));
                                }
                            }
                        }
                    }
                } else {
                    if (fieldMetaData.length() != 0) {
                        fieldMetaData.append(BalSyntaxConstants.COMMA_WITH_NEWLINE);
                    }
                    fieldMetaData.append(String.format(BalSyntaxConstants.METADATA_RECORD_FIELD_TEMPLATE,
                            field.getFieldName(), BalSyntaxUtils.stripEscapeCharacter(field.getFieldName())));
                }
            }
            if (associateFieldMetaData.length() > 1) {
                fieldMetaData.append(",");
                fieldMetaData.append(associateFieldMetaData);
            }
            entityMetaData.append(String.format(BalSyntaxConstants.FIELD_METADATA_TEMPLATE, fieldMetaData));
            entityMetaData.append(BalSyntaxConstants.COMMA_SPACE);

            StringBuilder keyFields = new StringBuilder();
            for (EntityField key : entity.getKeys()) {
                if (keyFields.length() != 0) {
                    keyFields.append(BalSyntaxConstants.COMMA_SPACE);
                }
                keyFields.append("\"").append(BalSyntaxUtils.stripEscapeCharacter(key.getFieldName())).append("\"");
            }
            entityMetaData.append(String.format(BalSyntaxConstants.METADATA_RECORD_KEY_FIELD_TEMPLATE, keyFields));
            if (relationsExists) {
                entityMetaData.append(BalSyntaxConstants.COMMA_SPACE);
                String joinMetaData = getJoinMetaData(entity);
                entityMetaData.append(String.format(BalSyntaxConstants.JOIN_METADATA_TEMPLATE, joinMetaData));
            }

            mapBuilder.append(String.format(BalSyntaxConstants.METADATA_RECORD_ELEMENT_TEMPLATE,
                    BalSyntaxUtils.getStringWithUnderScore(entity.getEntityName()), entityMetaData));
        }
        return NodeParser.parseObjectMember(String.format(BalSyntaxConstants.METADATA_RECORD_TEMPLATE, mapBuilder));
    }

    private static String getJoinMetaData(Entity entity) {
        StringBuilder joinMetaData = new StringBuilder();
        for (EntityField entityField : entity.getFields()) {
            StringBuilder refColumns = new StringBuilder();
            StringBuilder joinColumns = new StringBuilder();
            if (entityField.getRelation() != null) {
                String relationType = "persist:ONE_TO_ONE";
                Entity associatedEntity = entityField.getRelation().getAssocEntity();
                for (EntityField associatedEntityField : associatedEntity.getFields()) {
                    if (associatedEntityField.getFieldType().equals(entity.getEntityName())) {
                        if (associatedEntityField.isArrayType() && !entityField.isArrayType()) {
                            relationType = "persist:ONE_TO_MANY";
                        } else if (!associatedEntityField.isArrayType() && entityField.isArrayType()) {
                            relationType = "persist:MANY_TO_ONE";
                        } else if (associatedEntityField.isArrayType() && entityField.isArrayType()) {
                            relationType = "persist:MANY_TO_MANY";
                        }
                    }
                }
                if (joinMetaData.length() > 0) {
                    joinMetaData.append(BalSyntaxConstants.COMMA_WITH_NEWLINE);
                }
                for (Relation.Key key : entityField.getRelation().getKeyColumns()) {
                    if (joinColumns.length() > 0) {
                        joinColumns.append(",");
                    }
                    if (refColumns.length() > 0) {
                        refColumns.append(",");
                    }
                    refColumns.append(String.format(BalSyntaxConstants.COLUMN_ARRAY_ENTRY_TEMPLATE,
                            key.getReference()));
                    joinColumns.append(String.format(BalSyntaxConstants.COLUMN_ARRAY_ENTRY_TEMPLATE, key.getField()));
                }
                joinMetaData.append(String.format(BalSyntaxConstants.JOIN_METADATA_FIELD_TEMPLATE,
                        entityField.getFieldName(), entityField.getFieldType(),
                        entityField.getFieldName(), entityField.getFieldType(), refColumns,
                        joinColumns, relationType));
            }
        }
        return joinMetaData.toString();
    }

    private static void addFunctionBodyToPostResource(Function create, List<EntityField> primaryKeys,
                                                      String tableName, String parameterType) {
        String insertStatement = String.format(BalSyntaxConstants.CREATE_SQL_RESULTS, tableName);
        create.addStatement(NodeParser.parseStatement(
                String.format(BalSyntaxConstants.LOCK_TEMPLATE, insertStatement)));
        create.addStatement(NodeParser.parseStatement(
                String.format(BalSyntaxConstants.RETURN_CREATED_KEY, parameterType)));
        StringBuilder filterKeys = new StringBuilder();
        for (int i = 0; i < primaryKeys.size(); i++) {
            filterKeys.append("inserted.").append(primaryKeys.get(i).getFieldName());
            if (i < primaryKeys.size() - 1) {
                filterKeys.append(",");
            }
        }
        if (primaryKeys.size() == 1) {
            filterKeys = new StringBuilder(BalSyntaxConstants.SELECT_WITH_SPACE + filterKeys +
                    BalSyntaxConstants.SEMICOLON);
        } else {
            filterKeys = new StringBuilder(BalSyntaxConstants.SELECT_WITH_SPACE + BalSyntaxConstants.OPEN_BRACKET +
                    filterKeys + BalSyntaxConstants.CLOSE_BRACKET + BalSyntaxConstants.SEMICOLON);
        }
        create.addStatement(NodeParser.parseStatement(filterKeys.toString()));
    }
}
