import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = { title: { default: "FiscalWatch AI", template: "%s | FiscalWatch AI" },
  description: "Publicações oficiais, impactos técnicos e evidências para equipes de ERP." };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="pt-BR"><body>
    <a href="#conteudo" className="skip-link">Pular para o conteúdo</a>
    <div className="app-shell">
      <aside className="sidebar" aria-label="Navegação principal">
        <Link href="/" className="brand"><span className="brand-mark" aria-hidden="true">FW</span><span>FiscalWatch <b>AI</b></span></Link>
        <p className="nav-label">INTELIGÊNCIA FISCAL</p>
        <nav><Link href="/">Início <span aria-hidden="true">↗</span></Link><Link href="/#consultar">Consultar publicação <span aria-hidden="true">→</span></Link></nav>
        <div className="sidebar-note"><span className="small-dot" /> FONTES OFICIAIS<p>Contexto técnico para decisões no seu ERP.</p></div>
      </aside>
      <div className="workspace"><header className="topbar"><span>Publicações e análises de impacto</span><span className="workspace-tag">Ambiente fiscal</span></header>
        <main id="conteudo" tabIndex={-1}>{children}</main>
        <footer>FiscalWatch AI <span>Informação oficial. Decisões com evidências.</span></footer>
      </div>
    </div>
  </body></html>;
}
