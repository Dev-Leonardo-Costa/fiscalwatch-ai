from app.model.schema_comparison import SchemaArtifact
from app.service.xsd_schema_comparison_service import compare_schema_artifacts


def test_deve_detectar_arquivo_adicionado():
    changes = compare_schema_artifacts(
        previous_artifacts=[],
        current_artifacts=[
            SchemaArtifact(
                path="schemas/novo.xsd",
                content=criar_schema(
                    "<xs:element name=\"NFe\" type=\"TNFe\"/>"
                )
            )
        ]
    )

    assert len(changes) == 1
    assert changes[0].artifact == "schemas/novo.xsd"
    assert changes[0].change_type == "FILE_ADDED"
    assert changes[0].schema_path is None
    assert changes[0].symbol_name is None
    assert changes[0].before is None
    assert changes[0].after is not None


def test_deve_detectar_arquivo_removido():
    changes = compare_schema_artifacts(
        previous_artifacts=[
            SchemaArtifact(
                path="schemas/antigo.xsd",
                content=criar_schema(
                    "<xs:element name=\"NFe\" type=\"TNFe\"/>"
                )
            )
        ],
        current_artifacts=[]
    )

    assert len(changes) == 1
    assert changes[0].artifact == "schemas/antigo.xsd"
    assert changes[0].change_type == "FILE_REMOVED"
    assert changes[0].schema_path is None
    assert changes[0].symbol_name is None
    assert changes[0].before is not None
    assert changes[0].after is None


def test_deve_detectar_elemento_adicionado():
    previous = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:complexType name="TNFe">
                <xs:sequence>
                    <xs:element name="infNFe" type="TInfNFe"/>
                </xs:sequence>
            </xs:complexType>
            """
        )
    )
    current = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:complexType name="TNFe">
                <xs:sequence>
                    <xs:element name="infNFe" type="TInfNFe"/>
                    <xs:element name="infNFeSupl" type="TInfNFeSupl"/>
                </xs:sequence>
            </xs:complexType>
            """
        )
    )

    changes = compare_schema_artifacts([previous], [current])

    assert [change.change_type for change in changes] == ["ELEMENT_ADDED"]
    assert changes[0].artifact == "schemas/nfe.xsd"
    assert changes[0].schema_path == "complexType:TNFe/element:infNFeSupl"
    assert changes[0].symbol_name == "infNFeSupl"
    assert changes[0].before is None
    assert changes[0].after["type"] == "TInfNFeSupl"


def test_deve_detectar_elemento_removido():
    previous = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:complexType name="TNFe">
                <xs:sequence>
                    <xs:element name="infNFe" type="TInfNFe"/>
                    <xs:element name="infNFeSupl" type="TInfNFeSupl"/>
                </xs:sequence>
            </xs:complexType>
            """
        )
    )
    current = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:complexType name="TNFe">
                <xs:sequence>
                    <xs:element name="infNFe" type="TInfNFe"/>
                </xs:sequence>
            </xs:complexType>
            """
        )
    )

    changes = compare_schema_artifacts([previous], [current])

    assert [change.change_type for change in changes] == ["ELEMENT_REMOVED"]
    assert changes[0].artifact == "schemas/nfe.xsd"
    assert changes[0].schema_path == "complexType:TNFe/element:infNFeSupl"
    assert changes[0].symbol_name == "infNFeSupl"
    assert changes[0].before["type"] == "TInfNFeSupl"
    assert changes[0].after is None


def test_deve_detectar_type_alterado():
    previous = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:complexType name="TNFe">
                <xs:sequence>
                    <xs:element name="valor" type="TDec_1302"/>
                </xs:sequence>
            </xs:complexType>
            """
        )
    )
    current = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:complexType name="TNFe">
                <xs:sequence>
                    <xs:element name="valor" type="TDec_1304"/>
                </xs:sequence>
            </xs:complexType>
            """
        )
    )

    changes = compare_schema_artifacts([previous], [current])

    assert [change.change_type for change in changes] == ["TYPE_CHANGED"]
    assert changes[0].schema_path == "complexType:TNFe/element:valor"
    assert changes[0].symbol_name == "valor"
    assert changes[0].before == "TDec_1302"
    assert changes[0].after == "TDec_1304"


def test_deve_detectar_min_occurs_zero_para_default_um():
    previous = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:complexType name="TNFe">
                <xs:sequence>
                    <xs:element name="obs" type="TObs" minOccurs="0"/>
                </xs:sequence>
            </xs:complexType>
            """
        )
    )
    current = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:complexType name="TNFe">
                <xs:sequence>
                    <xs:element name="obs" type="TObs"/>
                </xs:sequence>
            </xs:complexType>
            """
        )
    )

    changes = compare_schema_artifacts([previous], [current])

    assert [change.change_type for change in changes] == [
        "CARDINALITY_CHANGED"
    ]
    assert changes[0].schema_path == "complexType:TNFe/element:obs"
    assert changes[0].before == {
        "minOccurs": "0",
        "maxOccurs": "1"
    }
    assert changes[0].after == {
        "minOccurs": "1",
        "maxOccurs": "1"
    }


def test_deve_detectar_max_occurs_alterado():
    previous = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:complexType name="TNFe">
                <xs:sequence>
                    <xs:element name="det" type="TDet" maxOccurs="990"/>
                </xs:sequence>
            </xs:complexType>
            """
        )
    )
    current = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:complexType name="TNFe">
                <xs:sequence>
                    <xs:element name="det" type="TDet" maxOccurs="unbounded"/>
                </xs:sequence>
            </xs:complexType>
            """
        )
    )

    changes = compare_schema_artifacts([previous], [current])

    assert [change.change_type for change in changes] == [
        "CARDINALITY_CHANGED"
    ]
    assert changes[0].schema_path == "complexType:TNFe/element:det"
    assert changes[0].before == {
        "minOccurs": "1",
        "maxOccurs": "990"
    }
    assert changes[0].after == {
        "minOccurs": "1",
        "maxOccurs": "unbounded"
    }


def test_nao_deve_colidir_elementos_com_mesmo_nome_em_pais_diferentes():
    previous = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:complexType name="TEmit">
                <xs:sequence>
                    <xs:element name="CNPJ" type="TCnpj"/>
                </xs:sequence>
            </xs:complexType>
            <xs:complexType name="TDest">
                <xs:sequence>
                    <xs:element name="CNPJ" type="TCnpj"/>
                </xs:sequence>
            </xs:complexType>
            """
        )
    )
    current = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:complexType name="TEmit">
                <xs:sequence>
                    <xs:element name="CNPJ" type="TCnpj"/>
                </xs:sequence>
            </xs:complexType>
            <xs:complexType name="TDest">
                <xs:sequence>
                    <xs:element name="CNPJ" type="TCnpjOpcional" minOccurs="0"/>
                </xs:sequence>
            </xs:complexType>
            """
        )
    )

    changes = compare_schema_artifacts([previous], [current])

    assert [change.change_type for change in changes] == [
        "TYPE_CHANGED",
        "CARDINALITY_CHANGED"
    ]
    assert all(
        change.schema_path == "complexType:TDest/element:CNPJ"
        for change in changes
    )


def test_schemas_iguais_nao_devem_gerar_mudancas():
    artifact = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:complexType name="TNFe">
                <xs:sequence>
                    <xs:element name="infNFe" type="TInfNFe"/>
                </xs:sequence>
            </xs:complexType>
            """
        )
    )

    changes = compare_schema_artifacts([artifact], [artifact])

    assert changes == []


def test_deve_detectar_enumeracoes_adicionadas_agrupadas():
    previous = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:simpleType name="TModeloDocumento">
                <xs:restriction base="xs:string">
                    <xs:enumeration value="01"/>
                    <xs:enumeration value="02"/>
                </xs:restriction>
            </xs:simpleType>
            """
        )
    )
    current = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:simpleType name="TModeloDocumento">
                <xs:restriction base="xs:string">
                    <xs:enumeration value="01"/>
                    <xs:enumeration value="02"/>
                    <xs:enumeration value="03"/>
                    <xs:enumeration value="04"/>
                </xs:restriction>
            </xs:simpleType>
            """
        )
    )

    changes = compare_schema_artifacts([previous], [current])

    assert [change.change_type for change in changes] == [
        "ENUMERATION_ADDED"
    ]
    assert changes[0].schema_path == (
        "simpleType:TModeloDocumento/restriction:xs:string"
    )
    assert changes[0].symbol_name == "TModeloDocumento"
    assert changes[0].before == []
    assert changes[0].after == ["03", "04"]


def test_deve_detectar_enumeracoes_removidas_agrupadas():
    previous = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:simpleType name="TModeloDocumento">
                <xs:restriction base="xs:string">
                    <xs:enumeration value="01"/>
                    <xs:enumeration value="02"/>
                    <xs:enumeration value="03"/>
                </xs:restriction>
            </xs:simpleType>
            """
        )
    )
    current = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:simpleType name="TModeloDocumento">
                <xs:restriction base="xs:string">
                    <xs:enumeration value="01"/>
                    <xs:enumeration value="02"/>
                </xs:restriction>
            </xs:simpleType>
            """
        )
    )

    changes = compare_schema_artifacts([previous], [current])

    assert [change.change_type for change in changes] == [
        "ENUMERATION_REMOVED"
    ]
    assert changes[0].schema_path == (
        "simpleType:TModeloDocumento/restriction:xs:string"
    )
    assert changes[0].symbol_name == "TModeloDocumento"
    assert changes[0].before == ["03"]
    assert changes[0].after == []


def test_deve_detectar_pattern_alterado():
    previous = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:simpleType name="TChaveAcesso">
                <xs:restriction base="xs:string">
                    <xs:pattern value="[0-9]{44}"/>
                </xs:restriction>
            </xs:simpleType>
            """
        )
    )
    current = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:simpleType name="TChaveAcesso">
                <xs:restriction base="xs:string">
                    <xs:pattern value="[0-9]{42}"/>
                </xs:restriction>
            </xs:simpleType>
            """
        )
    )

    changes = compare_schema_artifacts([previous], [current])

    assert [change.change_type for change in changes] == [
        "PATTERN_CHANGED"
    ]
    assert changes[0].schema_path == (
        "simpleType:TChaveAcesso/restriction:xs:string"
    )
    assert changes[0].symbol_name == "TChaveAcesso"
    assert changes[0].before == "[0-9]{44}"
    assert changes[0].after == "[0-9]{42}"


def test_deve_detectar_max_length_alterado():
    changes = comparar_facet(
        facet="maxLength",
        before_value="20",
        after_value="30"
    )

    assert [change.change_type for change in changes] == ["FACET_CHANGED"]
    assert changes[0].before == {"maxLength": "20"}
    assert changes[0].after == {"maxLength": "30"}


def test_deve_detectar_min_length_alterado():
    changes = comparar_facet(
        facet="minLength",
        before_value="1",
        after_value="2"
    )

    assert [change.change_type for change in changes] == ["FACET_CHANGED"]
    assert changes[0].before == {"minLength": "1"}
    assert changes[0].after == {"minLength": "2"}


def test_deve_detectar_total_digits_alterado():
    changes = comparar_facet(
        facet="totalDigits",
        before_value="13",
        after_value="15"
    )

    assert [change.change_type for change in changes] == ["FACET_CHANGED"]
    assert changes[0].before == {"totalDigits": "13"}
    assert changes[0].after == {"totalDigits": "15"}


def test_deve_detectar_fraction_digits_alterado():
    changes = comparar_facet(
        facet="fractionDigits",
        before_value="2",
        after_value="4"
    )

    assert [change.change_type for change in changes] == ["FACET_CHANGED"]
    assert changes[0].before == {"fractionDigits": "2"}
    assert changes[0].after == {"fractionDigits": "4"}


def test_deve_detectar_facet_adicionada():
    previous = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:simpleType name="TCodigo">
                <xs:restriction base="xs:string"/>
            </xs:simpleType>
            """
        )
    )
    current = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:simpleType name="TCodigo">
                <xs:restriction base="xs:string">
                    <xs:length value="2"/>
                </xs:restriction>
            </xs:simpleType>
            """
        )
    )

    changes = compare_schema_artifacts([previous], [current])

    assert [change.change_type for change in changes] == ["FACET_CHANGED"]
    assert changes[0].schema_path == "simpleType:TCodigo/restriction:xs:string"
    assert changes[0].symbol_name == "TCodigo"
    assert changes[0].before == {}
    assert changes[0].after == {"length": "2"}


def test_deve_detectar_include_adicionado():
    previous = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema("")
    )
    current = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            "<xs:include schemaLocation=\"tipos_v1.xsd\"/>"
        )
    )

    changes = compare_schema_artifacts([previous], [current])

    assert [change.change_type for change in changes] == ["INCLUDE_CHANGED"]
    assert changes[0].schema_path == "include[0]"
    assert changes[0].symbol_name == "tipos_v1.xsd"
    assert changes[0].before is None
    assert changes[0].after == {"schemaLocation": "tipos_v1.xsd"}


def test_deve_detectar_include_removido():
    previous = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            "<xs:include schemaLocation=\"tipos_v1.xsd\"/>"
        )
    )
    current = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema("")
    )

    changes = compare_schema_artifacts([previous], [current])

    assert [change.change_type for change in changes] == ["INCLUDE_CHANGED"]
    assert changes[0].schema_path == "include[0]"
    assert changes[0].symbol_name == "tipos_v1.xsd"
    assert changes[0].before == {"schemaLocation": "tipos_v1.xsd"}
    assert changes[0].after is None


def test_deve_detectar_include_com_schema_location_alterado():
    previous = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            "<xs:include schemaLocation=\"tipos_v1.xsd\"/>"
        )
    )
    current = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            "<xs:include schemaLocation=\"tipos_v2.xsd\"/>"
        )
    )

    changes = compare_schema_artifacts([previous], [current])

    assert [change.change_type for change in changes] == ["INCLUDE_CHANGED"]
    assert changes[0].schema_path == "include[0]"
    assert changes[0].symbol_name == "tipos_v2.xsd"
    assert changes[0].before == {"schemaLocation": "tipos_v1.xsd"}
    assert changes[0].after == {"schemaLocation": "tipos_v2.xsd"}


def test_deve_detectar_import_adicionado():
    previous = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema("")
    )
    current = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:import
                namespace="urn:assinatura"
                schemaLocation="assinatura_v1.xsd"/>
            """
        )
    )

    changes = compare_schema_artifacts([previous], [current])

    assert [change.change_type for change in changes] == ["IMPORT_CHANGED"]
    assert changes[0].schema_path == "import[0]"
    assert changes[0].symbol_name == "urn:assinatura"
    assert changes[0].before is None
    assert changes[0].after == {
        "namespace": "urn:assinatura",
        "schemaLocation": "assinatura_v1.xsd"
    }


def test_deve_detectar_import_removido():
    previous = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:import
                namespace="urn:assinatura"
                schemaLocation="assinatura_v1.xsd"/>
            """
        )
    )
    current = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema("")
    )

    changes = compare_schema_artifacts([previous], [current])

    assert [change.change_type for change in changes] == ["IMPORT_CHANGED"]
    assert changes[0].schema_path == "import[0]"
    assert changes[0].symbol_name == "urn:assinatura"
    assert changes[0].before == {
        "namespace": "urn:assinatura",
        "schemaLocation": "assinatura_v1.xsd"
    }
    assert changes[0].after is None


def test_deve_detectar_import_com_schema_location_alterado():
    previous = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:import
                namespace="urn:assinatura"
                schemaLocation="assinatura_v1.xsd"/>
            """
        )
    )
    current = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:import
                namespace="urn:assinatura"
                schemaLocation="assinatura_v2.xsd"/>
            """
        )
    )

    changes = compare_schema_artifacts([previous], [current])

    assert [change.change_type for change in changes] == ["IMPORT_CHANGED"]
    assert changes[0].schema_path == "import[0]"
    assert changes[0].symbol_name == "urn:assinatura"
    assert changes[0].before == {
        "namespace": "urn:assinatura",
        "schemaLocation": "assinatura_v1.xsd"
    }
    assert changes[0].after == {
        "namespace": "urn:assinatura",
        "schemaLocation": "assinatura_v2.xsd"
    }


def test_deve_detectar_import_com_namespace_alterado():
    previous = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:import
                namespace="urn:assinatura:v1"
                schemaLocation="assinatura.xsd"/>
            """
        )
    )
    current = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:import
                namespace="urn:assinatura:v2"
                schemaLocation="assinatura.xsd"/>
            """
        )
    )

    changes = compare_schema_artifacts([previous], [current])

    assert [change.change_type for change in changes] == ["IMPORT_CHANGED"]
    assert changes[0].schema_path == "import[0]"
    assert changes[0].symbol_name == "urn:assinatura:v2"
    assert changes[0].before == {
        "namespace": "urn:assinatura:v1",
        "schemaLocation": "assinatura.xsd"
    }
    assert changes[0].after == {
        "namespace": "urn:assinatura:v2",
        "schemaLocation": "assinatura.xsd"
    }


def test_schemas_com_includes_e_imports_iguais_nao_devem_gerar_mudancas():
    artifact = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            """
            <xs:include schemaLocation="tipos_v1.xsd"/>
            <xs:import
                namespace="urn:assinatura"
                schemaLocation="assinatura_v1.xsd"/>
            """
        )
    )

    changes = compare_schema_artifacts([artifact], [artifact])

    assert changes == []


def comparar_facet(
    facet: str,
    before_value: str,
    after_value: str
):
    previous = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            f"""
            <xs:simpleType name="TValor">
                <xs:restriction base="xs:string">
                    <xs:{facet} value="{before_value}"/>
                </xs:restriction>
            </xs:simpleType>
            """
        )
    )
    current = SchemaArtifact(
        path="schemas/nfe.xsd",
        content=criar_schema(
            f"""
            <xs:simpleType name="TValor">
                <xs:restriction base="xs:string">
                    <xs:{facet} value="{after_value}"/>
                </xs:restriction>
            </xs:simpleType>
            """
        )
    )

    changes = compare_schema_artifacts([previous], [current])

    assert changes[0].schema_path == "simpleType:TValor/restriction:xs:string"
    assert changes[0].symbol_name == "TValor"

    return changes


def criar_schema(inner_xml: str) -> str:
    return (
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
        f"{inner_xml}"
        "</xs:schema>"
    )
