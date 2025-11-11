const supabaseUrl = 'https://warlxiwqmdherlsyvwkq.supabase.co'
const supabaseKey = 'sb_publishable_660Hp569VljMC5vcb3qkyw_XdWpEj_t'


import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
export const supabase = createClient(supabaseUrl, supabaseKey)