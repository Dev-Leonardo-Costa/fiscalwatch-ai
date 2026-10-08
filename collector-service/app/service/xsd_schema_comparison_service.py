from dataclasses import dataclass
from xml.etree import ElementTree

from app.model.schema_comparison import (
    SchemaArtifact,
    SchemaChange,
    XsdTypeDefinition
)


XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema"
XSD_ELEMENT_TAG = f"{{{XSD_NAMESPACE}}}element"
XSD_COMPLEX_TYPE_TAG = f"{{{XSD_NAMESPACE}}}complexType"
XSD_SIMPLE_TYPE_TAG = f"{{{XSD_NAMESPACE}}}simpleType"
XSD_RESTRICTION_TAG = f"{{{XSD_NAMESPACE}}}restriction"
XSD_ENUMERATION_TAG = f"{{{XSD_NAMESPACE}}}enumeration"
XSD_PATTERN_TAG = f"{{{XSD_NAMESPACE}}}pattern"
XSD_INCLUDE_TAG = f"{{{XSD_NAMESPACE}}}include"
XSD_IMPORT_TAG = f"{{{XSD_NAMESPACE}}}import"
SUPPORTED_FACET_TAGS = {
    f"{{{XSD_NAMESPACE}}}whiteSpace": "whiteSpace",
    f"{{{XSD_NAMESPACE}}}length": "length",
    f"{{{XSD_NAMESPACE}}}minLength": "minLength",
    f"{{{XSD_NAMESPACE}}}maxLength": "maxLength",
    f"{{{XSD_NAMESPACE}}}minInclusive": "minInclusive",
    f"{{{XSD_NAMESPACE}}}maxInclusive": "maxInclusive",
    f"{{{XSD_NAMESPACE}}}minExclusive": "minExclusive",
    f"{{{XSD_NAMESPACE}}}maxExclusive": "maxExclusive",
    f"{{{XSD_NAMESPACE}}}totalDigits": "totalDigits",
    f"{{{XSD_NAMESPACE}}}fractionDigits": "fractionDigits"
}


@dataclass(frozen=True)
class _ElementDescriptor:
    schema_path: str
    symbol_name: str
    type: str | None
    min_occurs: str
    max_occurs: str

    def snapshot(self) -> dict[str, str | None]:
        return {
            "name": self.symbol_name,
            "type": self.type,
            "minOccurs": self.min_occurs,
            "maxOccurs": self.max_occurs
        }

    def cardinality(self) -> dict[str, str]:
        return {
            "minOccurs": self.min_occurs,
            "maxOccurs": self.max_occurs
        }


@dataclass(frozen=True)
class _RestrictionDescriptor:
    schema_path: str
    symbol_name: str
    base: str | None
    enumerations: list[str]
    patterns: list[str]
    facets: dict[str, str]

    def pattern_value(self) -> str | list[str] | None:
        if not self.patterns:
            return None

        if len(self.patterns) == 1:
            return self.patterns[0]

        return self.patterns


@dataclass(frozen=True)
class _IncludeDescriptor:
    schema_path: str
    schema_location: str | None

    @property
    def symbol_name(self) -> str | None:
        return self.schema_location

    def snapshot(self) -> dict[str, str | None]:
        return {
            "schemaLocation": self.schema_location
        }


@dataclass(frozen=True)
class _ImportDescriptor:
    schema_path: str
    namespace: str | None
    schema_location: str | None

    @property
    def symbol_name(self) -> str | None:
        return self.namespace or self.schema_location

    def snapshot(self) -> dict[str, str | None]:
        return {
            "namespace": self.namespace,
            "schemaLocation": self.schema_location
        }


def compare_schema_artifacts(
    previous_artifacts: list[SchemaArtifact],
    current_artifacts: list[SchemaArtifact]
) -> list[SchemaChange]:
    previous_by_path = _index_artifacts_by_path(previous_artifacts)
    current_by_path = _index_artifacts_by_path(current_artifacts)
    previous_restrictions_by_path = _parse_restrictions_by_path(
        previous_artifacts
    )
    current_restrictions_by_path = _parse_restrictions_by_path(
        current_artifacts
    )
    previous_type_definitions = _index_xsd_type_definitions(
        previous_restrictions_by_path
    )
    current_type_definitions = _index_xsd_type_definitions(
        current_restrictions_by_path
    )
    changes = []

    for path in sorted(current_by_path.keys() - previous_by_path.keys()):
        artifact = current_by_path[path]
        changes.append(
            SchemaChange(
                artifact=path,
                change_type="FILE_ADDED",
                after=_artifact_snapshot(artifact)
            )
        )

    for path in sorted(previous_by_path.keys() - current_by_path.keys()):
        artifact = previous_by_path[path]
        changes.append(
            SchemaChange(
                artifact=path,
                change_type="FILE_REMOVED",
                before=_artifact_snapshot(artifact)
            )
        )

    for path in sorted(previous_by_path.keys() & current_by_path.keys()):
        changes.extend(
            _compare_artifact_elements(
                previous_by_path[path],
                current_by_path[path],
                previous_type_definitions,
                current_type_definitions
            )
        )
        changes.extend(
            _compare_artifact_restrictions(
                previous_by_path[path],
                current_by_path[path],
                previous_restrictions_by_path.get(path, {}),
                current_restrictions_by_path.get(path, {})
            )
        )
        changes.extend(
            _compare_artifact_references(
                previous_by_path[path],
                current_by_path[path]
            )
        )

    return changes


def _index_artifacts_by_path(
    artifacts: list[SchemaArtifact]
) -> dict[str, SchemaArtifact]:
    return {
        artifact.path: artifact
        for artifact in artifacts
    }


def _artifact_snapshot(artifact: SchemaArtifact) -> dict[str, str | None]:
    return {
        "path": artifact.path,
        "content_hash": artifact.content_hash
    }


def _compare_artifact_elements(
    previous_artifact: SchemaArtifact,
    current_artifact: SchemaArtifact,
    previous_type_definitions: dict[str, XsdTypeDefinition],
    current_type_definitions: dict[str, XsdTypeDefinition]
) -> list[SchemaChange]:
    previous_elements = _parse_xsd_elements(previous_artifact.content)
    current_elements = _parse_xsd_elements(current_artifact.content)
    changes = []

    for schema_path in sorted(
        current_elements.keys() - previous_elements.keys()
    ):
        element = current_elements[schema_path]
        changes.append(
            SchemaChange(
                artifact=current_artifact.path,
                change_type="ELEMENT_ADDED",
                schema_path=schema_path,
                symbol_name=element.symbol_name,
                before=None,
                after=element.snapshot()
            )
        )

    for schema_path in sorted(
        previous_elements.keys() - current_elements.keys()
    ):
        element = previous_elements[schema_path]
        changes.append(
            SchemaChange(
                artifact=previous_artifact.path,
                change_type="ELEMENT_REMOVED",
                schema_path=schema_path,
                symbol_name=element.symbol_name,
                before=element.snapshot(),
                after=None
            )
        )

    for schema_path in sorted(
        previous_elements.keys() & current_elements.keys()
    ):
        previous = previous_elements[schema_path]
        current = current_elements[schema_path]

        if previous.type != current.type:
            changes.append(
                SchemaChange(
                    artifact=current_artifact.path,
                    change_type="TYPE_CHANGED",
                    schema_path=schema_path,
                    symbol_name=current.symbol_name,
                    before=previous.type,
                    after=current.type,
                    before_type_definition=_find_type_definition(
                        previous.type,
                        previous_type_definitions
                    ),
                    after_type_definition=_find_type_definition(
                        current.type,
                        current_type_definitions
                    )
                )
            )

        if previous.cardinality() != current.cardinality():
            changes.append(
                SchemaChange(
                    artifact=current_artifact.path,
                    change_type="CARDINALITY_CHANGED",
                    schema_path=schema_path,
                    symbol_name=current.symbol_name,
                    before=previous.cardinality(),
                    after=current.cardinality()
                )
            )

    return changes


def _compare_artifact_restrictions(
    previous_artifact: SchemaArtifact,
    current_artifact: SchemaArtifact,
    previous_restrictions: dict[str, _RestrictionDescriptor],
    current_restrictions: dict[str, _RestrictionDescriptor]
) -> list[SchemaChange]:
    changes = []

    for schema_path in sorted(
        previous_restrictions.keys() & current_restrictions.keys()
    ):
        previous = previous_restrictions[schema_path]
        current = current_restrictions[schema_path]

        added_enumerations = [
            value
            for value in current.enumerations
            if value not in previous.enumerations
        ]

        if added_enumerations:
            changes.append(
                SchemaChange(
                    artifact=current_artifact.path,
                    change_type="ENUMERATION_ADDED",
                    schema_path=schema_path,
                    symbol_name=current.symbol_name,
                    before=[],
                    after=added_enumerations
                )
            )

        removed_enumerations = [
            value
            for value in previous.enumerations
            if value not in current.enumerations
        ]

        if removed_enumerations:
            changes.append(
                SchemaChange(
                    artifact=current_artifact.path,
                    change_type="ENUMERATION_REMOVED",
                    schema_path=schema_path,
                    symbol_name=current.symbol_name,
                    before=removed_enumerations,
                    after=[]
                )
            )

        if previous.pattern_value() != current.pattern_value():
            changes.append(
                SchemaChange(
                    artifact=current_artifact.path,
                    change_type="PATTERN_CHANGED",
                    schema_path=schema_path,
                    symbol_name=current.symbol_name,
                    before=previous.pattern_value(),
                    after=current.pattern_value()
                )
            )

        changed_facets = _changed_facets(
            previous.facets,
            current.facets
        )

        if changed_facets:
            changes.append(
                SchemaChange(
                    artifact=current_artifact.path,
                    change_type="FACET_CHANGED",
                    schema_path=schema_path,
                    symbol_name=current.symbol_name,
                    before=changed_facets[0],
                    after=changed_facets[1]
                )
            )

    return changes


def _compare_artifact_references(
    previous_artifact: SchemaArtifact,
    current_artifact: SchemaArtifact
) -> list[SchemaChange]:
    previous_includes = _parse_xsd_includes(previous_artifact.content)
    current_includes = _parse_xsd_includes(current_artifact.content)
    previous_imports = _parse_xsd_imports(previous_artifact.content)
    current_imports = _parse_xsd_imports(current_artifact.content)

    return [
        *_compare_reference_lists(
            artifact_path=current_artifact.path,
            change_type="INCLUDE_CHANGED",
            previous_references=previous_includes,
            current_references=current_includes
        ),
        *_compare_reference_lists(
            artifact_path=current_artifact.path,
            change_type="IMPORT_CHANGED",
            previous_references=previous_imports,
            current_references=current_imports
        )
    ]


def _compare_reference_lists(
    artifact_path: str,
    change_type: str,
    previous_references: list[_IncludeDescriptor | _ImportDescriptor],
    current_references: list[_IncludeDescriptor | _ImportDescriptor]
) -> list[SchemaChange]:
    changes = []
    max_length = max(
        len(previous_references),
        len(current_references)
    )

    for index in range(max_length):
        previous = _get_reference(previous_references, index)
        current = _get_reference(current_references, index)

        if previous == current:
            continue

        reference = current or previous

        changes.append(
            SchemaChange(
                artifact=artifact_path,
                change_type=change_type,
                schema_path=reference.schema_path,
                symbol_name=reference.symbol_name,
                before=previous.snapshot() if previous else None,
                after=current.snapshot() if current else None
            )
        )

    return changes


def _parse_xsd_elements(content: str) -> dict[str, _ElementDescriptor]:
    root = ElementTree.fromstring(content)
    elements = {}

    _collect_xsd_elements(
        node=root,
        current_path=[],
        elements=elements
    )

    return elements


def _parse_xsd_restrictions(
    content: str
) -> dict[str, _RestrictionDescriptor]:
    root = ElementTree.fromstring(content)
    restrictions = {}

    _collect_xsd_restrictions(
        node=root,
        current_path=[],
        restrictions=restrictions
    )

    return restrictions


def _parse_xsd_includes(content: str) -> list[_IncludeDescriptor]:
    root = ElementTree.fromstring(content)
    includes = []

    for index, node in enumerate(root.findall(XSD_INCLUDE_TAG)):
        includes.append(
            _IncludeDescriptor(
                schema_path=f"include[{index}]",
                schema_location=node.attrib.get("schemaLocation")
            )
        )

    return includes


def _parse_xsd_imports(content: str) -> list[_ImportDescriptor]:
    root = ElementTree.fromstring(content)
    imports = []

    for index, node in enumerate(root.findall(XSD_IMPORT_TAG)):
        imports.append(
            _ImportDescriptor(
                schema_path=f"import[{index}]",
                namespace=node.attrib.get("namespace"),
                schema_location=node.attrib.get("schemaLocation")
            )
        )

    return imports


def _parse_restrictions_by_path(
    artifacts: list[SchemaArtifact]
) -> dict[str, dict[str, _RestrictionDescriptor]]:
    return {
        artifact.path: _parse_xsd_restrictions(artifact.content)
        for artifact in artifacts
    }


def _index_xsd_type_definitions(
    restrictions_by_path: dict[str, dict[str, _RestrictionDescriptor]]
) -> dict[str, XsdTypeDefinition]:
    definitions = {}

    for artifact_path in sorted(restrictions_by_path):
        for restriction in restrictions_by_path[artifact_path].values():
            if not restriction.schema_path.startswith("simpleType:"):
                continue

            definitions.setdefault(
                restriction.symbol_name,
                XsdTypeDefinition(
                    name=restriction.symbol_name,
                    artifact=artifact_path,
                    schema_path=restriction.schema_path,
                    base=restriction.base,
                    patterns=restriction.patterns,
                    enumerations=restriction.enumerations,
                    facets=restriction.facets
                )
            )

    return definitions


def _find_type_definition(
    type_name: str | None,
    definitions: dict[str, XsdTypeDefinition]
) -> XsdTypeDefinition | None:
    if not type_name:
        return None

    return definitions.get(type_name) or definitions.get(
        type_name.split(":", 1)[-1]
    )


def _collect_xsd_elements(
    node: ElementTree.Element,
    current_path: list[str],
    elements: dict[str, _ElementDescriptor]
) -> None:
    next_path = _extend_schema_path(current_path, node)

    if node.tag == XSD_ELEMENT_TAG:
        name = node.attrib.get("name")

        if name:
            schema_path = "/".join(next_path)
            elements[schema_path] = _ElementDescriptor(
                schema_path=schema_path,
                symbol_name=name,
                type=node.attrib.get("type"),
                min_occurs=node.attrib.get("minOccurs", "1"),
                max_occurs=node.attrib.get("maxOccurs", "1")
            )

    for child in list(node):
        _collect_xsd_elements(
            node=child,
            current_path=next_path,
            elements=elements
        )


def _collect_xsd_restrictions(
    node: ElementTree.Element,
    current_path: list[str],
    restrictions: dict[str, _RestrictionDescriptor]
) -> None:
    next_path = _extend_schema_path(current_path, node)

    if node.tag == XSD_RESTRICTION_TAG:
        base = node.attrib.get("base", "desconhecido")
        schema_path = "/".join([
            *current_path,
            f"restriction:{base}"
        ])

        if current_path:
            symbol_name = current_path[-1].split(":", 1)[-1]
        else:
            symbol_name = base

        restrictions[schema_path] = _RestrictionDescriptor(
            schema_path=schema_path,
            symbol_name=symbol_name,
            base=base,
            enumerations=_collect_direct_values(node, XSD_ENUMERATION_TAG),
            patterns=_collect_direct_values(node, XSD_PATTERN_TAG),
            facets=_collect_direct_facets(node)
        )

    for child in list(node):
        _collect_xsd_restrictions(
            node=child,
            current_path=next_path,
            restrictions=restrictions
        )


def _extend_schema_path(
    current_path: list[str],
    node: ElementTree.Element
) -> list[str]:
    if node.tag == XSD_COMPLEX_TYPE_TAG:
        name = node.attrib.get("name")

        if name:
            return [
                *current_path,
                f"complexType:{name}"
            ]

    if node.tag == XSD_SIMPLE_TYPE_TAG:
        name = node.attrib.get("name")

        if name:
            return [
                *current_path,
                f"simpleType:{name}"
            ]

    if node.tag == XSD_ELEMENT_TAG:
        name = node.attrib.get("name")

        if name:
            return [
                *current_path,
                f"element:{name}"
            ]

    return current_path


def _collect_direct_values(
    node: ElementTree.Element,
    tag: str
) -> list[str]:
    return [
        child.attrib["value"]
        for child in list(node)
        if child.tag == tag and "value" in child.attrib
    ]


def _collect_direct_facets(
    node: ElementTree.Element
) -> dict[str, str]:
    facets = {}

    for child in list(node):
        facet_name = SUPPORTED_FACET_TAGS.get(child.tag)

        if facet_name and "value" in child.attrib:
            facets[facet_name] = child.attrib["value"]

    return facets


def _changed_facets(
    previous_facets: dict[str, str],
    current_facets: dict[str, str]
) -> tuple[dict[str, str], dict[str, str]] | None:
    previous_changed = {}
    current_changed = {}

    for facet_name in sorted(
        previous_facets.keys() | current_facets.keys()
    ):
        previous_value = previous_facets.get(facet_name)
        current_value = current_facets.get(facet_name)

        if previous_value == current_value:
            continue

        if previous_value is not None:
            previous_changed[facet_name] = previous_value

        if current_value is not None:
            current_changed[facet_name] = current_value

    if not previous_changed and not current_changed:
        return None

    return previous_changed, current_changed


def _get_reference(
    references: list[_IncludeDescriptor | _ImportDescriptor],
    index: int
) -> _IncludeDescriptor | _ImportDescriptor | None:
    if index >= len(references):
        return None

    return references[index]
