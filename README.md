# Bedreflyt DT API

The Bedreflyt DT API is a RESTful API that provides access to the operations over the database, triplestore, and simulations. The API is built using SpringBoot over Kotlin, and it is designed to be run over Docker Compose.

## Running the API

To run the API, you can either build it using Docker, or you can setup a development environment environment (e.g. IntelliJ IDEA). When running the API, you need to ensure that the following environment variables are set:

- `DB_DIALECT` that specifies the dialect of the database. Default value is `org.hibernate.dialect.PostgreSQLDialect`.
- `DB_DRIVER` that specifies the driver of the database. Default value is `org.postgresql.Driver`.
- `DB_PASSWORD` that specifies the password of the database.
- `DB_SCHEMA` that specifies the schema of the database.
- `DB_URL` that specifies the URL of the database.
- `DB_USER` that specifies the user of the database.
- `DOMAIN_PREFIX_URI` that specifies the URI of the domain prefix. The default one is `http://www.smolang.org/bedreflyt#`
- `EXTRA_PREFIXES` that specifies the extra prefixes to be used in the triplestore, it should be `ast,http://www.smolang.org/bedreflyt#`
- `SMOL_PATH` that specifies the path to the SMOL files to be loaded in the triplestore. The default should be set to `src/main/resources/SMOL/Bedreflyt_data.smol;src/main/resources/SMOL/Bedreflyt_diagnosis.smol;src/main/resources/SMOL/Bedreflyt_roomCategories.smol;src/main/resources/SMOL/Bedreflyt_rooms.smol;src/main/resources/SMOL/Bedreflyt_tasks.smol;src/main/resources/SMOL/Bedreflyt_taskDependencies.smol;src/main/resources/SMOL/Bedreflyt_treatments.smol;src/main/resources/SMOL/Bedreflyt.smol` (The SMOL folder can be moved into the root folder when used with Docker)
- `SOLVER_ENDPOINT` that specifies the endpoint of the solver. The default value is `localhost` and will expect it to be an API listening under port 8000 (e.g. fastapi running Z3)
- `TRIPLESTORE_DATASET` that specifies the dataset to be used in the triplestore. The value should be configured the one used in the triplestore.
- `TRIPLESTORE_URL` that specifies the URL of the triplestore. The default value is `localhost`

## Triplestore

The triplestore is a RDF database that stores the data in triples. The triplestore is used to store the data of the simulations, and it is used to query the data and accessed using the SPARQL query language. For this project we used [Apache Jena Fuseki](https://jena.apache.org/download/) and we used the configuration present in the `config.ttl` file. The triplestore can be run with the command

```bash
$FUSEKI_PATH/fuseki-server --update --config config.ttl
```

where `$FUSEKI_PATH` is the path to the Apache Jena Fuseki downloaded.

## Database relation

The database relation is a PostgreSQL database that stores the data of the application. The database is used to store the data of the users, the simulations, and the allocations. The database is accessed using the Hibernate ORM. The database can be run using the Docker Compose file present in the root folder. The relations of the database are as follow

![ER Diagram](./graphics/Bedreflyt-EntitiyRelation.png)
