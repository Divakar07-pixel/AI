// Deploy with: supabase functions deploy assistant-chat
// Keep OPENAI_API_KEY in Supabase secrets; never put it in the Android app.
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};

type ChatMessage = { role: "user" | "assistant"; content: string };

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (request.method !== "POST") {
    return json({ error: "Method not allowed." }, 405);
  }

  const apiKey = Deno.env.get("OPENAI_API_KEY");
  if (!apiKey) return json({ error: "Server AI provider is not configured." }, 503);

  let payload: { messages?: unknown };
  try {
    payload = await request.json();
  } catch {
    return json({ error: "Request body must be valid JSON." }, 400);
  }

  if (!Array.isArray(payload.messages) || payload.messages.length < 1 || payload.messages.length > 20) {
    return json({ error: "Send between 1 and 20 conversation messages." }, 400);
  }

  const messages: ChatMessage[] = [];
  for (const item of payload.messages) {
    if (
      !item ||
      (item.role !== "user" && item.role !== "assistant") ||
      typeof item.content !== "string" ||
      item.content.trim().length === 0 ||
      item.content.length > 3000
    ) {
      return json({ error: "Each message must have a valid role and 1–3000 characters." }, 400);
    }
    messages.push({ role: item.role, content: item.content.trim() });
  }

  try {
    const response = await fetch("https://api.openai.com/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: Deno.env.get("OPENAI_MODEL") ?? "gpt-4o-mini",
        messages: [
          {
            role: "system",
            content: "You are a helpful, friendly Android voice assistant. Answer clearly and concisely. You may explain how to do phone tasks, but you cannot perform device actions yourself. Never claim you changed settings, sent messages, placed calls, or executed commands. The Android app separately validates device commands and requires confirmation for sensitive actions.",
          },
          ...messages,
        ],
        max_tokens: 600,
        temperature: 0.5,
      }),
    });

    if (!response.ok) {
      // Do not forward provider response bodies, which can contain implementation details.
      console.error("AI provider returned HTTP", response.status);
      return json({ error: "AI provider request failed. Check server configuration or quota." }, 502);
    }

    const result = await response.json();
    const reply = result?.choices?.[0]?.message?.content;
    if (typeof reply !== "string" || reply.trim().length === 0) {
      return json({ error: "AI provider returned no text." }, 502);
    }
    return json({ reply: reply.trim().slice(0, 12000) }, 200);
  } catch (error) {
    console.error("AI function failed:", error instanceof Error ? error.message : "unknown error");
    return json({ error: "AI service is temporarily unavailable." }, 502);
  }
});

function json(body: Record<string, unknown>, status: number): Response {
  return new Response(JSON.stringify(body), { status, headers: corsHeaders });
}
