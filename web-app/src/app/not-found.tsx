import Link from "next/link";
export default function NotFound() {
  return <div className="page-stack"><span className="eyebrow">REGISTRO NÃO ENCONTRADO</span><h1>Publicação não encontrada</h1><p>Confira o identificador e consulte uma publicação já registrada.</p><Link className="button" href="/">Voltar à consulta</Link></div>;
}
