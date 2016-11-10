package lagom

import com.lightbend.markdown.theme.MarkdownTheme
import play.doc.{Toc, TocTree}
import play.twirl.api.Html

object LagomMarkdownTheme extends MarkdownTheme {
  override def renderPage(projectName: Option[String], title: Option[String], home: String, content: Html,
    sidebar: Option[Html], breadcrumbs: Option[Html], apiDocs: Seq[(String, String)], sourceUrl: Option[String]): Html =
    html.documentation(projectName, title, home, content, sidebar, breadcrumbs, apiDocs, sourceUrl)

  override def renderNextLink(toc: TocTree): Html = html.nextLink(toc)

  override def renderSidebar(hierarchy: List[Toc]): Html = html.sidebar(hierarchy)

  override def renderBreadcrumbs(hierarchy: List[Toc]): Html = html.breadcrumbs(hierarchy)
}
