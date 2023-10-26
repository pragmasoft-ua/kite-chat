## Class Preloader
It's a maven project that is used for obtaining Java classes which were loaded by JVM
via **JAVA_TOOL_OPTIONS: -verbose:class** option.
It uses AWS SDK to access CloudWatch's LogGroups and obtain LogEvents which
have the following pattern: **[23.935s][info][class,load] java.time.DayOfWeek**.
After getting LogEvents it processes them to get only Java Class
and save to **output** file afterwards.

### Usage
In order to use this project you can utilize **class-preloader.sh** script.

Example:
```bash
./class-preloader.sh -groups "/aws/lambda/request-dispatcher"
```
You can pass the following params:
1) **-groups** (required) [here you need to specify your LogGroups' names (use SPACE as a delimiter)] (at least one must be specified)
2) **-limit** [here you can specify the number of LogStreams per LogGroup which script will use to extract LogEvents from] (_default value = 3_)
3) **-output** [here you can specify the output file where found classes will be stored] (_default value = ../k1te-serverless/src/main/resources/META-INF/quarkus-preload-classes.txt_)
