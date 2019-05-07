package lagom

import com.lightbend.markdown.theme.MarkdownTheme
import play.doc.Toc
import play.doc.TocTree
import play.twirl.api.Html

object LagomMarkdownTheme extends MarkdownTheme {
  override def renderPage(
      projectName: Option[String],
      title: Option[String],
      home: String,
      content: Html,
      sidebar: Option[Html],
      breadcrumbs: Option[Html],
      apiDocs: Seq[(String, String)],
      sourceUrl: Option[String]
  ): Html =
    html.documentation(projectName, title, home, content, sidebar, breadcrumbs, apiDocs, sourceUrl)

  override def renderNextLinks(nextLinks: List[TocTree]): Html = html.nextLinks(nextLinks)

  override def renderSidebar(hierarchy: List[Toc]): Html = html.sidebar(hierarchy)

  override def renderBreadcrumbs(hierarchy: List[Toc]): Html = html.breadcrumbs(hierarchy)
}
