This project explores the option of integration between Lagom and Akka Projection to implement multi-tenant
functionality. In this service we injected multiple persistence plugin for each  Tenant.
Each Tenant will have it own separate journal store(write-side). Then Akka projection is used to read the event from
each individual journal store and populate query side(read-side) table as well as publish event to Kafka.


development setup - run from root folder
```
sbt
sbt:hello-world> clean
sbt:hello-world> compile
sbt:hello-world> runAll
```

Step to test .

We need to configure the tenant configuration . sample configuration is provided in ../resources/tenant*.conf file.
To enable a Tenant we need to add tenat*.conf file in application.conf include . Once tenant conf is enabled , we need to 
tenant plugin detail to the auto read configuration.

````

akka {
  extensions = [akka.persistence.Persistence]
  persistence {
    journal {
      auto-start-journals = ["tenant.cassandra-journal-plugin.t1","tenant.cassandra-journal-plugin.t2"]
    }
    snapshot-store {
      auto-start-snapshot-stores = ["tenant.cassandra-snapshot-store-plugin.t1","tenant.cassandra-snapshot-store-plugin.t2"]
    }
  }
}

````

Once the configuration is complete we can start the service.
````
sbt:hello-world> runAll
````

Once the service is started, we can execute following curl request from another terminal.
Following request are for tenat T1. Similarly we can modify the request for Tenant t2 or any other.

To create stock entity in tenant T1
````
curl --location --request POST 'http://localhost:9000/api/v1/stock/t1' \
--header 'Content-Type: application/json' \
--data-raw '{
    
    "stockId":"abcdefg120",
    "price":9.0,
    "name":"netflix"

}'
````
To create portfolio entity in tenant T1
````
curl --location --request POST 'http://localhost:9000/api/v1/portfolio/t1/1' \
--header 'Content-Type: application/json' \
--data-raw ' [
        {
            "stockId": "abcdefg321",
            "units": 5
        },
        {
            "stockId": "abcdefg320",
            "units": 5
        }
    
    ]
'
````
GET stock entity by ID for tenant t1
````
curl --location --request GET 'http://localhost:9000/api/v1/stock/t1/abcdefg120'
````
GET portfolio entity by ID for tenant t1
````
curl --location --request GET 'http://localhost:9000/api/v1/portfolio/t1/1'
````
GETALL stock entity for tenant t1
````
http://localhost:9000/api/v1/stock/t1
````

GETALL portfolio entity for tenant t1
````
curl --location --request GET 'http://localhost:9000/api/v1/portfolio/t1'
````



