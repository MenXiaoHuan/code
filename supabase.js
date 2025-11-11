// 替换为你的 Project URL 和 anon key
const supabaseUrl = 'https://warlxiwqmdherlsyvwkq.supabase.co'
const supabaseKey = 'sb_publishable_660Hp569VljMC5vcb3qkyw_XdWpEj_t'

// 引入 Supabase JS 库
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

// 初始化客户端
export const supabase = createClient(supabaseUrl, supabaseKey)