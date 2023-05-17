// AUTO-GENERATED FILE. DO NOT MODIFY.

// This file is an auto-generated file by Ballerina persistence layer for model.
// It should not be modified by hand.

import ballerina/persist;
import ballerina/jballerina.java;
import ballerinax/mysql;
import ballerinax/mysql.driver as _;

const PROFILE = "profiles";
const USER = "users";
const DEPT = "depts";
const CUSTOMER = "customers";
const STUDENT = "students";
const MULTIPLE_ASSOCIATIONS = "multipleassociations";

public isolated client class Client {
    *persist:AbstractPersistClient;

    private final mysql:Client dbClient;

    private final map<persist:SQLClient> persistClients = {};

    private final record {|persist:SQLMetadata...;|} metadata = {
        [PROFILE] : {
            entityName: "Profile",
            tableName: `Profile`,
            fieldMetadata: {
                id: {columnName: "id"},
                name: {columnName: "name"},
                isAdult: {columnName: "isAdult"},
                salary: {columnName: "salary"},
                age: {columnName: "age"},
                isRegistered: {columnName: "isRegistered"},
                ownerId: {columnName: "ownerId"},
                "owner.id": {relation: {entityName: "owner", refField: "id"}},
                "owner.name": {relation: {entityName: "owner", refField: "name"}},
                "owner.multipleassociationsId": {relation: {entityName: "owner", refField: "multipleassociationsId"}}
            },
            keyFields: ["id"],
            joinMetadata: {owner: {entity: User, fieldName: "owner", refTable: "User", refColumns: ["id"], joinColumns: ["ownerId"], 'type: persist:ONE_TO_ONE}}
        },
        [USER] : {
            entityName: "User",
            tableName: `User`,
            fieldMetadata: {
                id: {columnName: "id"},
                name: {columnName: "name"},
                multipleassociationsId: {columnName: "multipleassociationsId"},
                "profile.id": {relation: {entityName: "profile", refField: "id"}},
                "profile.name": {relation: {entityName: "profile", refField: "name"}},
                "profile.isAdult": {relation: {entityName: "profile", refField: "isAdult"}},
                "profile.salary": {relation: {entityName: "profile", refField: "salary"}},
                "profile.age": {relation: {entityName: "profile", refField: "age"}},
                "profile.isRegistered": {relation: {entityName: "profile", refField: "isRegistered"}},
                "profile.ownerId": {relation: {entityName: "profile", refField: "ownerId"}},
                "multipleAssociations.id": {relation: {entityName: "multipleAssociations", refField: "id"}},
                "multipleAssociations.name": {relation: {entityName: "multipleAssociations", refField: "name"}}
            },
            keyFields: ["id"],
            joinMetadata: {
                profile: {entity: Profile, fieldName: "profile", refTable: "Profile", refColumns: ["ownerId"], joinColumns: ["id"], 'type: persist:ONE_TO_ONE},
                multipleAssociations: {entity: MultipleAssociations, fieldName: "multipleAssociations", refTable: "MultipleAssociations", refColumns: ["id"], joinColumns: ["multipleassociationsId"], 'type: persist:ONE_TO_ONE}
            }
        },
        [DEPT] : {
            entityName: "Dept",
            tableName: `Dept`,
            fieldMetadata: {
                id: {columnName: "id"},
                name: {columnName: "name"},
                multipleassociationsId: {columnName: "multipleassociationsId"},
                "multipleAssociations.id": {relation: {entityName: "multipleAssociations", refField: "id"}},
                "multipleAssociations.name": {relation: {entityName: "multipleAssociations", refField: "name"}}
            },
            keyFields: ["id"],
            joinMetadata: {multipleAssociations: {entity: MultipleAssociations, fieldName: "multipleAssociations", refTable: "MultipleAssociations", refColumns: ["id"], joinColumns: ["multipleassociationsId"], 'type: persist:ONE_TO_ONE}}
        },
        [CUSTOMER] : {
            entityName: "Customer",
            tableName: `Customer`,
            fieldMetadata: {
                id: {columnName: "id"},
                name: {columnName: "name"},
                age: {columnName: "age"},
                multipleassociationsId: {columnName: "multipleassociationsId"},
                "multipleAssociations.id": {relation: {entityName: "multipleAssociations", refField: "id"}},
                "multipleAssociations.name": {relation: {entityName: "multipleAssociations", refField: "name"}}
            },
            keyFields: ["id"],
            joinMetadata: {multipleAssociations: {entity: MultipleAssociations, fieldName: "multipleAssociations", refTable: "MultipleAssociations", refColumns: ["id"], joinColumns: ["multipleassociationsId"], 'type: persist:ONE_TO_ONE}}
        },
        [STUDENT] : {
            entityName: "Student",
            tableName: `Student`,
            fieldMetadata: {
                id: {columnName: "id"},
                firstName: {columnName: "firstName"},
                age: {columnName: "age"},
                lastName: {columnName: "lastName"},
                nicNo: {columnName: "nicNo"}
            },
            keyFields: ["id", "firstName"]
        },
        [MULTIPLE_ASSOCIATIONS] : {
            entityName: "MultipleAssociations",
            tableName: `MultipleAssociations`,
            fieldMetadata: {
                id: {columnName: "id"},
                name: {columnName: "name"},
                "owner.id": {relation: {entityName: "owner", refField: "id"}},
                "owner.name": {relation: {entityName: "owner", refField: "name"}},
                "owner.multipleassociationsId": {relation: {entityName: "owner", refField: "multipleassociationsId"}},
                "dept.id": {relation: {entityName: "dept", refField: "id"}},
                "dept.name": {relation: {entityName: "dept", refField: "name"}},
                "dept.multipleassociationsId": {relation: {entityName: "dept", refField: "multipleassociationsId"}},
                "customer.id": {relation: {entityName: "customer", refField: "id"}},
                "customer.name": {relation: {entityName: "customer", refField: "name"}},
                "customer.age": {relation: {entityName: "customer", refField: "age"}},
                "customer.multipleassociationsId": {relation: {entityName: "customer", refField: "multipleassociationsId"}}
            },
            keyFields: ["id"],
            joinMetadata: {
                owner: {entity: User, fieldName: "owner", refTable: "User", refColumns: ["multipleassociationsId"], joinColumns: ["id"], 'type: persist:ONE_TO_ONE},
                dept: {entity: Dept, fieldName: "dept", refTable: "Dept", refColumns: ["multipleassociationsId"], joinColumns: ["id"], 'type: persist:ONE_TO_ONE},
                customer: {entity: Customer, fieldName: "customer", refTable: "Customer", refColumns: ["multipleassociationsId"], joinColumns: ["id"], 'type: persist:ONE_TO_ONE}
            }
        }
    };

    public function init() returns persist:Error? {
        mysql:Client|error dbClient = new (host = host, user = user, password = password, database = database, port = port, options = connectionOptions);
        if dbClient is error {
            return <persist:Error>error(dbClient.message());
        }
        self.dbClient = dbClient;
        lock {
            self.persistClients[PROFILE] = check new (self.dbClient, self.metadata.get(PROFILE));
            self.persistClients[USER] = check new (self.dbClient, self.metadata.get(USER));
            self.persistClients[DEPT] = check new (self.dbClient, self.metadata.get(DEPT));
            self.persistClients[CUSTOMER] = check new (self.dbClient, self.metadata.get(CUSTOMER));
            self.persistClients[STUDENT] = check new (self.dbClient, self.metadata.get(STUDENT));
            self.persistClients[MULTIPLE_ASSOCIATIONS] = check new (self.dbClient, self.metadata.get(MULTIPLE_ASSOCIATIONS));
        }
    }

    isolated resource function get profiles(ProfileTargetType targetType = <>) returns stream<targetType, persist:Error?> = @java:Method {
        'class: "io.ballerina.stdlib.persist.datastore.MySQLProcessor",
        name: "query"
    } external;

    isolated resource function get profiles/[int id](ProfileTargetType targetType = <>) returns targetType|persist:Error = @java:Method {
        'class: "io.ballerina.stdlib.persist.datastore.MySQLProcessor",
        name: "queryOne"
    } external;

    isolated resource function post profiles(ProfileInsert[] data) returns int[]|persist:Error {
        lock {
            _ = check self.persistClients.get(PROFILE).runBatchInsertQuery(data.clone());
        }
        return from ProfileInsert inserted in data
            select inserted.id;
    }

    isolated resource function put profiles/[int id](ProfileUpdate value) returns Profile|persist:Error {
        lock {
            _ = check self.persistClients.get(PROFILE).runUpdateQuery(id, value.clone());
        }
        return self->/profiles/[id].get();
    }

    isolated resource function delete profiles/[int id]() returns Profile|persist:Error {
        Profile result = check self->/profiles/[id].get();
        lock {
            _ = check self.persistClients.get(PROFILE).runDeleteQuery(id);
        }
        return result;
    }

    isolated resource function get users(UserTargetType targetType = <>) returns stream<targetType, persist:Error?> = @java:Method {
        'class: "io.ballerina.stdlib.persist.datastore.MySQLProcessor",
        name: "query"
    } external;

    isolated resource function get users/[int id](UserTargetType targetType = <>) returns targetType|persist:Error = @java:Method {
        'class: "io.ballerina.stdlib.persist.datastore.MySQLProcessor",
        name: "queryOne"
    } external;

    isolated resource function post users(UserInsert[] data) returns int[]|persist:Error {
        lock {
            _ = check self.persistClients.get(USER).runBatchInsertQuery(data.clone());
        }
        return from UserInsert inserted in data
            select inserted.id;
    }

    isolated resource function put users/[int id](UserUpdate value) returns User|persist:Error {
        lock {
            _ = check self.persistClients.get(USER).runUpdateQuery(id, value.clone());
        }
        return self->/users/[id].get();
    }

    isolated resource function delete users/[int id]() returns User|persist:Error {
        User result = check self->/users/[id].get();
        lock {
            _ = check self.persistClients.get(USER).runDeleteQuery(id);
        }
        return result;
    }

    isolated resource function get depts(DeptTargetType targetType = <>) returns stream<targetType, persist:Error?> = @java:Method {
        'class: "io.ballerina.stdlib.persist.datastore.MySQLProcessor",
        name: "query"
    } external;

    isolated resource function get depts/[int id](DeptTargetType targetType = <>) returns targetType|persist:Error = @java:Method {
        'class: "io.ballerina.stdlib.persist.datastore.MySQLProcessor",
        name: "queryOne"
    } external;

    isolated resource function post depts(DeptInsert[] data) returns int[]|persist:Error {
        lock {
            _ = check self.persistClients.get(DEPT).runBatchInsertQuery(data.clone());
        }
        return from DeptInsert inserted in data
            select inserted.id;
    }

    isolated resource function put depts/[int id](DeptUpdate value) returns Dept|persist:Error {
        lock {
            _ = check self.persistClients.get(DEPT).runUpdateQuery(id, value.clone());
        }
        return self->/depts/[id].get();
    }

    isolated resource function delete depts/[int id]() returns Dept|persist:Error {
        Dept result = check self->/depts/[id].get();
        lock {
            _ = check self.persistClients.get(DEPT).runDeleteQuery(id);
        }
        return result;
    }

    isolated resource function get customers(CustomerTargetType targetType = <>) returns stream<targetType, persist:Error?> = @java:Method {
        'class: "io.ballerina.stdlib.persist.datastore.MySQLProcessor",
        name: "query"
    } external;

    isolated resource function get customers/[int id](CustomerTargetType targetType = <>) returns targetType|persist:Error = @java:Method {
        'class: "io.ballerina.stdlib.persist.datastore.MySQLProcessor",
        name: "queryOne"
    } external;

    isolated resource function post customers(CustomerInsert[] data) returns int[]|persist:Error {
        lock {
            _ = check self.persistClients.get(CUSTOMER).runBatchInsertQuery(data.clone());
        }
        return from CustomerInsert inserted in data
            select inserted.id;
    }

    isolated resource function put customers/[int id](CustomerUpdate value) returns Customer|persist:Error {
        lock {
            _ = check self.persistClients.get(CUSTOMER).runUpdateQuery(id, value.clone());
        }
        return self->/customers/[id].get();
    }

    isolated resource function delete customers/[int id]() returns Customer|persist:Error {
        Customer result = check self->/customers/[id].get();
        lock {
            _ = check self.persistClients.get(CUSTOMER).runDeleteQuery(id);
        }
        return result;
    }

    isolated resource function get students(StudentTargetType targetType = <>) returns stream<targetType, persist:Error?> = @java:Method {
        'class: "io.ballerina.stdlib.persist.datastore.MySQLProcessor",
        name: "query"
    } external;

    isolated resource function get students/[int id]/[string firstName](StudentTargetType targetType = <>) returns targetType|persist:Error = @java:Method {
        'class: "io.ballerina.stdlib.persist.datastore.MySQLProcessor",
        name: "queryOne"
    } external;

    isolated resource function post students(StudentInsert[] data) returns [int, string][]|persist:Error {
        lock {
            _ = check self.persistClients.get(STUDENT).runBatchInsertQuery(data.clone());
        }
        return from StudentInsert inserted in data
            select [inserted.id, inserted.firstName];
    }

    isolated resource function put students/[int id]/[string firstName](StudentUpdate value) returns Student|persist:Error {
        lock {
            _ = check self.persistClients.get(STUDENT).runUpdateQuery({"id": id, "firstName": firstName}, value.clone());
        }
        return self->/students/[id]/[firstName].get();
    }

    isolated resource function delete students/[int id]/[string firstName]() returns Student|persist:Error {
        Student result = check self->/students/[id]/[firstName].get();
        lock {
            _ = check self.persistClients.get(STUDENT).runDeleteQuery({"id": id, "firstName": firstName});
        }
        return result;
    }

    isolated resource function get multipleassociations(MultipleAssociationsTargetType targetType = <>) returns stream<targetType, persist:Error?> = @java:Method {
        'class: "io.ballerina.stdlib.persist.datastore.MySQLProcessor",
        name: "query"
    } external;

    isolated resource function get multipleassociations/[int id](MultipleAssociationsTargetType targetType = <>) returns targetType|persist:Error = @java:Method {
        'class: "io.ballerina.stdlib.persist.datastore.MySQLProcessor",
        name: "queryOne"
    } external;

    isolated resource function post multipleassociations(MultipleAssociationsInsert[] data) returns int[]|persist:Error {
        lock {
            _ = check self.persistClients.get(MULTIPLE_ASSOCIATIONS).runBatchInsertQuery(data.clone());
        }
        return from MultipleAssociationsInsert inserted in data
            select inserted.id;
    }

    isolated resource function put multipleassociations/[int id](MultipleAssociationsUpdate value) returns MultipleAssociations|persist:Error {
        lock {
            _ = check self.persistClients.get(MULTIPLE_ASSOCIATIONS).runUpdateQuery(id, value.clone());
        }
        return self->/multipleassociations/[id].get();
    }

    isolated resource function delete multipleassociations/[int id]() returns MultipleAssociations|persist:Error {
        MultipleAssociations result = check self->/multipleassociations/[id].get();
        lock {
            _ = check self.persistClients.get(MULTIPLE_ASSOCIATIONS).runDeleteQuery(id);
        }
        return result;
    }

    public function close() returns persist:Error? {
        error? result = self.dbClient.close();
        if result is error {
            return <persist:Error>error(result.message());
        }
        return result;
    }
}

