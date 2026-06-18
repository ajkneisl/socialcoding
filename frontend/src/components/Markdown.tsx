import ReactMarkdown, { type Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'

// Tailwind's preflight strips list markers and heading sizes, so the rendered
// markdown elements are styled explicitly to match the site's design.
const components: Components = {
    p: ({ children }) => <p className="mb-4 last:mb-0 text-text-soft">{children}</p>,
    a: ({ href, children }) => {
        const external = /^https?:\/\//.test(href ?? '')
        return (
            <a href={href} {...(external ? { target: '_blank', rel: 'noreferrer' } : {})}>
                {children}
            </a>
        )
    },
    ul: ({ children }) => (
        <ul className="mb-4 list-disc space-y-1 pl-5 text-text-soft last:mb-0">{children}</ul>
    ),
    ol: ({ children }) => (
        <ol className="mb-4 list-decimal space-y-1 pl-5 text-text-soft last:mb-0">{children}</ol>
    ),
    h1: ({ children }) => <h2 className="mb-2 mt-6 text-2xl first:mt-0">{children}</h2>,
    h2: ({ children }) => <h3 className="mb-2 mt-6 text-xl first:mt-0">{children}</h3>,
    h3: ({ children }) => <h4 className="mb-2 mt-5 text-lg first:mt-0">{children}</h4>,
    blockquote: ({ children }) => (
        <blockquote className="mb-4 border-l-2 border-line pl-4 italic text-text-faint">
            {children}
        </blockquote>
    ),
    pre: ({ children }) => (
        <pre className="mb-4 overflow-x-auto rounded-lg border border-line bg-panel p-3 text-[0.85rem]">
            {children}
        </pre>
    ),
    hr: () => <hr className="my-6 border-line" />,
    table: ({ children }) => (
        <div className="mb-4 overflow-x-auto">
            <table className="w-full border-collapse text-left text-[0.9rem]">{children}</table>
        </div>
    ),
    th: ({ children }) => (
        <th className="border border-line px-3 py-2 font-semibold">{children}</th>
    ),
    td: ({ children }) => <td className="border border-line px-3 py-2 text-text-soft">{children}</td>,
}

/** Renders a markdown string with the site's styling. Raw HTML is not rendered. */
export function Markdown({ children, className }: { children: string; className?: string }) {
    return (
        <div className={className}>
            <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
                {children}
            </ReactMarkdown>
        </div>
    )
}
