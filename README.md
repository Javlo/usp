# cdn

## instance for create a node of content delivery network

module to be installed on a J2EE server (tomcat for example)

### file structure

#### `~/data/javlo_cdn` : contains the cache of files.

The folder can be hot deleted to **renew the cache**.

#### `~/etc/javlo_cdn` : contains the definition of the hosts which can use the cdn/

a host is defined by a properties file:

`#context#.properties`: will be accessed by : `://#host of cdn#/#context#/#uri of resource#`

> example: javloorg.properties access: `://cdn.javlo.org/javloorg/img/image.png`

the properties file contains at least the value `url.target` which gives the source url to use the cdn.

the value `code.reset` contains the code for release the cache of this host.

##### example: 

```
url.target=https://www.javlo.org/
code.reset=qsdk12fjoi59dkk1
```