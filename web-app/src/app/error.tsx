"use client";
export default function ErrorPage({ reset }: { reset: () => void }) {
  return <div className="page-stack" role="alert"><span className="eyebrow">CONSULTA INTERROMPIDA</span><h1>Não foi possível carregar esta página</h1><p>Ocorreu um erro inesperado. Tente novamente em instantes.</p><button className="button" onClick={reset}>Tentar novamente</button></div>;
}
