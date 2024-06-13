require "active_support/all"
ENV["RAILS_ENV"] = ENV["RAILS_ENV"].presence || "test"

# ENV["RAILS_ENV"] = "test"

# puts "ENV['RAILS_ENV'] #{ENV['RAILS_ENV']}"

require "faker"
require "config/bundle"
require "config/rails"
require "config/database"
require "config/web"


Dir[Rails.root.join('datalayer/spec/factories/**/*.rb')].each { |f| require f }



# # puts ">> railsenv = #{Rails.root}"
# # puts ">> railsenv = #{ENV['RAILS_ENV']}"
#
# puts ">> abc = #{Dir[Rails.root.join('datalayer/spec/factories/**/*.rb')]}"
#
#
# # require '../datalayer/spec/factories/user'

require "shared/clients"
require "pry"
require "uuidtools"

RSpec.configure do |config|
  config.before :all do
    @spec_seed =
      ENV["SPEC_SEED"].presence.try(:strip) || `git log -n1 --format=%T`.strip
    puts "SPEC_SEED #{@spec_seed} set env SPEC_SEED to force value"
    srand Integer(@spec_seed, 16)
  end
  config.after :all do
    puts "SPEC_SEED #{@spec_seed} set env SPEC_SEED to force value"
  end

  config.example_status_persistence_file_path = "tmp/rspec_results.log.txt"
  config.include FactoryBot::Syntax::Methods
end
