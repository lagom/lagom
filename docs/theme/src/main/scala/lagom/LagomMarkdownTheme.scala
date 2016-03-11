package lagom

import com.lightbend.markdown.theme.MarkdownTheme
import play.doc.TocTree
import play.twirl.api.Html

object LagomMarkdownTheme extends MarkdownTheme {
  override def renderPage(projectName: Option[String], title: Option[String], home: String, content: Html,
    sidebar: Option[Html], apiDocs: Seq[(String, String)], sourceUrl: Option[String]): Html =
    html.documentation(projectName, title, home, content, sidebar, apiDocs, sourceUrl)

  override def renderNextLink(toc: TocTree): Html = html.nextLink(toc)
}
