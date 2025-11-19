const supabaseUrl = 'https://grvsrjfomcdwipfxljxb.supabase.co'
const supabaseKey = 'sb_publishable_YQ7V4jw2Q0ADlArd5Z6JLA_oG4BZ_ve'


import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'
export const supabase = createClient(supabaseUrl, supabaseKey)