package org.clyze.doop.jimple

import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor

/**
 * A POJO representation for the SARIF subset needed by Doop.
 */
@CompileStatic @TupleConstructor class SARIF extends MapGen {
    List<Run> runs
    /**
     * Basic entry point to the format: construct a SARIF object and call
     * this method to get a map that can be converted to JSON.
     * @return   a generic map that represents values to be converted to JSON
     */
    @Override Map<String, Object> toMap() {
        return [
            '$schema' : 'https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json',
            'version' : '2.1.0',
            'runs'    : fromList(runs)
        ] as Map<String, Object>
    }
}

abstract class MapGen {
    abstract Map<String, Object> toMap()
    static List<Map> fromList(List<? extends MapGen> l) {
        return l.collect { it.toMap() } as List<Map>
    }
}

@CompileStatic @TupleConstructor class Run extends MapGen {
    List<Result> results
    List<Artifact> artifacts
    Tool tool
    String columnKind = 'utf16CodeUnits'
    Props properties = new Props()
    @Override Map<String, Object> toMap() {
        return [
            'results' : fromList(results),
            'artifacts' : fromList(artifacts),
            'tool' : tool.toMap(),
            'columnKind' : columnKind,
            'properties' : properties.toMap()
        ] as Map<String, Object>
    }
}

@CompileStatic @TupleConstructor class Artifact extends MapGen {
    ArtifactLocation location
    @Override Map<String, Object> toMap() {
        return [ 'location' : location.toMap() ] as Map<String, Object>
    }
}

@CompileStatic @TupleConstructor class ArtifactLocation extends MapGen {
    String uri
    String uriBaseId = '%SRCROOT%'
    int index = 0
    @Override
    Map<String, Object> toMap() {
        return [ 'uri' : uri, 'uriBaseId' : uriBaseId, 'index' : index ] as Map<String, Object>
    }
}

@CompileStatic @TupleConstructor class Props extends MapGen {
    String format = 'doop-sarif-output'
    Map<String, Object> toMap() {
        return [ 'format' : format ] as Map<String, Object>
    }
}

@CompileStatic @TupleConstructor class Message extends MapGen {
    String text
    @Override Map<String, Object> toMap() {
        return [ 'text' : text ] as Map<String, Object>
    }
}

@CompileStatic @TupleConstructor class Result extends MapGen {
    String ruleId
    int ruleIndex
    Message message
    List<Location> locations
    @Override Map<String, Object> toMap() {
        return [ 'ruleId' : ruleId,
                 'ruleIndex' : ruleIndex,
                 'message' : message.toMap(),
                 'locations' : fromList(locations)] as Map<String, Object>
    }
}

@CompileStatic @TupleConstructor class Location extends MapGen {
    ArtifactLocation artifactLocation
    long startLine, startColumn, endLine, endColumn
    @Override
    Map<String, Object> toMap() {
        return ['physicalLocation' : [
                    'artifactLocation' : artifactLocation.toMap(),
                    'region' : [
                        'startLine'   : startLine,
                        'startColumn' : startColumn,
                        'endLine'     : endLine,
                        'endColumn'   : endColumn
                    ] as Map<String, Object>
                ] as Map<String, Object>
        ] as Map<String, Object>
    }
}

@CompileStatic @TupleConstructor class Tool extends MapGen {
    Driver driver
    @Override
    Map<String, Object> toMap() {
        return ['driver' : driver.toMap()] as Map<String, Object>
    }
}

@CompileStatic @TupleConstructor class Driver extends MapGen {
    String name, fullName, version, semanticVersion
    List<Rule> rules
    @Override Map<String, Object> toMap() {
        return ['name' : name,
                'fullName' : fullName,
                'rules' : fromList(rules),
                'version' : version,
                'semanticVersion' : semanticVersion
        ] as Map<String, Object>
    }
}

@CompileStatic @TupleConstructor class Rule extends MapGen {
    String id, name, shortDescription, fullDescription, level
    @Override Map<String, Object> toMap() {
        return ['id' : id,
                'name' : name,
                'shortDescription' : ['text' : shortDescription] as Map<String, Object>,
                'fullDescription' : ['text' : fullDescription] as Map<String, Object>,
                'defaultConfiguration' : ['level' : level ] as Map<String, Object>
        ] as Map<String, Object>
    }
}
